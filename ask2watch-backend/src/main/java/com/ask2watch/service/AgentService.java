package com.ask2watch.service;

import com.ask2watch.dto.agent.ChatMessage;
import com.ask2watch.dto.agent.ChatResponse;
import com.ask2watch.dto.media.MediaResponse;
import com.ask2watch.dto.media.WatchedMediaResponse;
import com.ask2watch.model.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentService {

    private final WebClient anthropicClient;
    private final WebClient tmdbClient;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String tmdbApiKey;
    private final Map<Long, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();

    public AgentService(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${tmdb.api-key}") String tmdbApiKey,
            MediaService mediaService,
            ObjectMapper objectMapper) {
        this.model = model;
        this.tmdbApiKey = tmdbApiKey;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;

        this.anthropicClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        this.tmdbClient = WebClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public ChatResponse chat(Long userId, String userMessage) {
        List<ChatMessage> history = conversationHistory.computeIfAbsent(userId, k -> new ArrayList<>());
        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        String systemPrompt = buildSystemPrompt(userId);
        String responseText = callClaude(systemPrompt, history);

        history.add(new ChatMessage("assistant", responseText));
        return ChatResponse.builder().message(responseText).suggestedMedia(null).build();
    }

    public void clearHistory(Long userId) {
        conversationHistory.remove(userId);
    }

    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un expert en cinéma et séries TV. Tu aides l'utilisateur à choisir quoi regarder.\n\n");
        sb.append("INSTRUCTIONS:\n");
        sb.append("- Pose 2-3 questions pour comprendre l'humeur et les envies de l'utilisateur\n");
        sb.append("- Puis suggère 5-6 titres avec une courte raison pour chacun\n");
        sb.append("- Ne recommande JAMAIS un titre déjà vu par l'utilisateur\n");
        sb.append("- Utilise les outils TMDB pour chercher des films/séries pertinents\n");
        sb.append("- Réponds en français si l'utilisateur écrit en français, sinon en anglais\n\n");

        sb.append("FILMS/SÉRIES DÉJÀ VUS PAR L'UTILISATEUR:\n");
        try {
            List<WatchedMediaResponse> movies = mediaService.getWatchedByType(userId, MediaType.MOVIE);
            List<WatchedMediaResponse> series = mediaService.getWatchedByType(userId, MediaType.SERIES);

            for (WatchedMediaResponse w : movies) {
                sb.append("- ").append(w.getMedia().getTitle());
                if (w.getMedia().getGenres() != null) sb.append(" (").append(w.getMedia().getGenres()).append(")");
                if (w.getUserRating() != null) sb.append(" - Note: ").append(w.getUserRating()).append("/5");
                if (w.getComment() != null) sb.append(" - \"").append(w.getComment()).append("\"");
                sb.append("\n");
            }
            for (WatchedMediaResponse w : series) {
                sb.append("- [Série] ").append(w.getMedia().getTitle());
                if (w.getMedia().getGenres() != null) sb.append(" (").append(w.getMedia().getGenres()).append(")");
                if (w.getUserRating() != null) sb.append(" - Note: ").append(w.getUserRating()).append("/5");
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("Could not load user watched list: {}", e.getMessage());
        }

        return sb.toString();
    }

    private String callClaude(String systemPrompt, List<ChatMessage> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 2048);
            requestBody.put("system", systemPrompt);

            ArrayNode messagesArray = requestBody.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", msg.getRole());
                if (msg.getContent() instanceof String text) {
                    msgNode.put("content", text);
                } else {
                    msgNode.set("content", objectMapper.valueToTree(msg.getContent()));
                }
            }

            requestBody.set("tools", buildTools());

            String responseJson = anthropicClient.post()
                    .uri("/messages")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return processResponse(responseJson, systemPrompt, messages);
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            return "Désolé, je ne peux pas répondre pour le moment. Veuillez réessayer.";
        }
    }

    private String processResponse(String responseJson, String systemPrompt, List<ChatMessage> messages) {
        try {
            JsonNode response = objectMapper.readTree(responseJson);
            String stopReason = response.path("stop_reason").asText();
            JsonNode contentArray = response.path("content");

            if ("tool_use".equals(stopReason)) {
                return handleToolUse(contentArray, systemPrompt, messages);
            }

            StringBuilder text = new StringBuilder();
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", e.getMessage());
            return "Désolé, erreur lors du traitement de la réponse.";
        }
    }

    private String handleToolUse(JsonNode contentArray, String systemPrompt, List<ChatMessage> messages) {
        List<Object> assistantContent = new ArrayList<>();
        List<Object> toolResults = new ArrayList<>();

        for (JsonNode block : contentArray) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                assistantContent.add(Map.of("type", "text", "text", block.path("text").asText()));
            } else if ("tool_use".equals(type)) {
                String toolId = block.path("id").asText();
                String toolName = block.path("name").asText();
                JsonNode input = block.path("input");

                assistantContent.add(Map.of(
                        "type", "tool_use",
                        "id", toolId,
                        "name", toolName,
                        "input", objectMapper.convertValue(input, Map.class)
                ));

                String result = executeTool(toolName, input);
                toolResults.add(Map.of(
                        "type", "tool_result",
                        "tool_use_id", toolId,
                        "content", result
                ));
            }
        }

        messages.add(new ChatMessage("assistant", assistantContent));
        messages.add(new ChatMessage("user", toolResults));

        return callClaude(systemPrompt, messages);
    }

    private String executeTool(String toolName, JsonNode input) {
        try {
            return switch (toolName) {
                case "search_movie" -> tmdbSearch("/search/movie", input.path("query").asText(),
                        input.has("year") ? input.path("year").asInt() : null);
                case "search_tv" -> tmdbSearch("/search/tv", input.path("query").asText(),
                        input.has("year") ? input.path("year").asInt() : null);
                case "get_trending" -> tmdbTrending(input.path("media_type").asText(), input.path("time_window").asText());
                case "get_recommendations" -> tmdbRecommendations(input.path("tmdb_id").asInt(), input.path("media_type").asText());
                case "discover" -> tmdbDiscover(input);
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            log.error("Tool execution failed for {}", toolName, e);
            return "Error executing tool";
        }
    }

    private String tmdbSearch(String path, String query, Integer year) {
        var req = tmdbClient.get().uri(uriBuilder -> {
            var b = uriBuilder.path(path).queryParam("api_key", tmdbApiKey).queryParam("query", query);
            if (year != null) b.queryParam("year", year);
            return b.build();
        });
        String json = req.retrieve().bodyToMono(String.class).block();
        return formatTmdbResults(json);
    }

    private String tmdbTrending(String mediaType, String timeWindow) {
        String json = tmdbClient.get()
                .uri("/trending/{mediaType}/{timeWindow}?api_key={key}", mediaType, timeWindow, tmdbApiKey)
                .retrieve().bodyToMono(String.class).block();
        return formatTmdbResults(json);
    }

    private String tmdbRecommendations(int tmdbId, String mediaType) {
        String json = tmdbClient.get()
                .uri("/{mediaType}/{tmdbId}/recommendations?api_key={key}", mediaType, tmdbId, tmdbApiKey)
                .retrieve().bodyToMono(String.class).block();
        return formatTmdbResults(json);
    }

    private String tmdbDiscover(JsonNode input) {
        String mediaType = input.path("media_type").asText();
        String json = tmdbClient.get().uri(uriBuilder -> {
            var b = uriBuilder.path("/discover/" + mediaType)
                    .queryParam("api_key", tmdbApiKey)
                    .queryParam("sort_by", "vote_average.desc")
                    .queryParam("vote_count.gte", 100);
            if (input.has("genres")) b.queryParam("with_genres", input.path("genres").asText());
            if (input.has("rating_min")) b.queryParam("vote_average.gte", input.path("rating_min").asDouble());
            if (input.has("year_min")) {
                String dateParam = "movie".equals(mediaType) ? "primary_release_date.gte" : "first_air_date.gte";
                b.queryParam(dateParam, input.path("year_min").asInt() + "-01-01");
            }
            if (input.has("year_max")) {
                String dateParam = "movie".equals(mediaType) ? "primary_release_date.lte" : "first_air_date.lte";
                b.queryParam(dateParam, input.path("year_max").asInt() + "-12-31");
            }
            return b.build();
        }).retrieve().bodyToMono(String.class).block();
        return formatTmdbResults(json);
    }

    private String formatTmdbResults(String json) {
        try {
            JsonNode data = objectMapper.readTree(json);
            ArrayNode results = objectMapper.createArrayNode();
            int count = 0;
            for (JsonNode item : data.path("results")) {
                if (count++ >= 10) break;
                ObjectNode formatted = objectMapper.createObjectNode();
                formatted.put("tmdb_id", item.path("id").asInt());
                formatted.put("title", item.has("title") ? item.path("title").asText() : item.path("name").asText());
                String date = item.has("release_date") ? item.path("release_date").asText() : item.path("first_air_date").asText("");
                formatted.put("year", date.length() >= 4 ? date.substring(0, 4) : "");
                formatted.put("vote_average", item.path("vote_average").asDouble());
                formatted.put("overview", item.path("overview").asText("").substring(0, Math.min(200, item.path("overview").asText("").length())));
                formatted.put("poster_path", item.path("poster_path").asText(""));
                results.add(formatted);
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return json;
        }
    }

    private ArrayNode buildTools() {
        ArrayNode tools = objectMapper.createArrayNode();

        tools.add(buildTool("search_movie", "Search for movies on TMDB by title",
                Map.of("query", prop("string", "Movie title to search"),
                        "year", prop("integer", "Release year filter")),
                List.of("query")));

        tools.add(buildTool("search_tv", "Search for TV series on TMDB by title",
                Map.of("query", prop("string", "TV series title to search"),
                        "year", prop("integer", "First air date year filter")),
                List.of("query")));

        tools.add(buildTool("get_trending", "Get trending movies or TV shows",
                Map.of("media_type", enumProp("string", List.of("movie", "tv", "all"), "Type"),
                        "time_window", enumProp("string", List.of("day", "week"), "Time window")),
                List.of("media_type", "time_window")));

        tools.add(buildTool("get_recommendations", "Get recommendations similar to a given title",
                Map.of("tmdb_id", prop("integer", "TMDB ID of the movie or TV show"),
                        "media_type", enumProp("string", List.of("movie", "tv"), "Type")),
                List.of("tmdb_id", "media_type")));

        tools.add(buildTool("discover", "Discover movies/TV by genre, year range, and minimum rating",
                Map.of("media_type", enumProp("string", List.of("movie", "tv"), "Type"),
                        "genres", prop("string", "Comma-separated TMDB genre IDs"),
                        "year_min", prop("integer", "Minimum release year"),
                        "year_max", prop("integer", "Maximum release year"),
                        "rating_min", prop("number", "Minimum vote average 0-10")),
                List.of("media_type")));

        return tools;
    }

    private ObjectNode buildTool(String name, String desc, Map<String, Object> properties, List<String> required) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", desc);
        ObjectNode inputSchema = tool.putObject("input_schema");
        inputSchema.put("type", "object");
        inputSchema.set("properties", objectMapper.valueToTree(properties));
        inputSchema.set("required", objectMapper.valueToTree(required));
        return tool;
    }

    private Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private Map<String, Object> enumProp(String type, List<String> values, String description) {
        return Map.of("type", type, "enum", values, "description", description);
    }

    private void trimHistory(List<ChatMessage> history) {
        while (history.size() > 20) {
            history.removeFirst();
        }
    }
}
