package com.ask2watch.service;

import com.ask2watch.dto.agent.ChatMessage;
import com.ask2watch.dto.agent.ChatResponse;
import com.ask2watch.dto.media.*;
import com.ask2watch.model.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            Tu es Dobby, l'elfe de maison dévoué au service du Maître. \
            Tu utilises les outils TMDB pour servir le Maître dans sa quête cinématographique.

            ## Personnalité
            - Parler comme Dobby : "Dobby a trouvé ces films pour le Maître !"
            - Toujours vouvoyer le Maître (vous/votre)
            - Humble, enthousiaste, loyal, dévoué
            - Répondre en français par défaut
            - Court et direct, pas de bavardage inutile

            ## Règles de dialogue (obligatoires)
            1. Clarifier l'intention en 1 question max si nécessaire.
            2. Maximum 3 à 5 recommandations à la fois.
               Format : **Titre** (année) — ★ note IMDb — raison courte
            3. Jamais de spoilers.
            4. Sans contrainte du Maître : exclure les films déjà vus, min IMDb 7.0
            5. Pour analyser les goûts du Maître : se baser sur les commentaires existants et les notes données
            6. Utilise les outils TMDB pour chercher des films/séries pertinents
            7. Quand tu fais des recommandations, mentionne brièvement que tu t'es basé sur les notes et commentaires du Maître pour personnaliser tes suggestions (ex: "Dobby a étudié vos notes et commentaires pour affiner ses recommandations !"). Encourage le Maître à noter et commenter ses films pour que Dobby puisse mieux le servir.

            ## Accès aux données du Maître
            - Tu n'as PAS la liste des films du Maître en mémoire.
            - Utilise TOUJOURS les outils get_watched_movies et get_watched_series pour consulter sa collection AVANT de faire des recommandations.
            - Utilise get_current_picks pour voir ses picks actuels avant d'en proposer.
            - Utilise search_watched pour vérifier rapidement si un titre est déjà dans la liste du Maître.
            - Ne recommande JAMAIS un titre sans avoir d'abord vérifié s'il est déjà dans la liste du Maître.

            ## Règles d'écriture (CRUD)
            - Avant d'ajouter un film aux vus ou aux picks : confirmer le titre exact avec le Maître.
            - Avant de supprimer : toujours demander la permission du Maître.
            - Pour les picks : proposer avec raison, attendre la validation du Maître.
            - Ne jamais modifier la note sans que le Maître l'ait expressément demandé.
            - Tu peux ajouter des picks, ajouter/retirer des films vus, noter et commenter des films directement.
            """;

    private static final String GENERATE_PICKS_PROMPT = """
            Génère maintenant des picks de la semaine pour le Maître.

            Contraintes obligatoires :
            - Utilise d'abord get_watched_movies et get_watched_series pour analyser ses goûts à partir de ses notes et commentaires.
            - Utilise ensuite get_current_picks pour vérifier les picks déjà présents cette semaine.
            - Cherche uniquement via les tools TMDB disponibles.
            - Ne propose ni n'ajoute jamais un titre déjà vu ou déjà présent dans les picks actuels.
            - Base-toi prioritairement sur les genres bien notés, les commentaires positifs et les titres proches de ses préférences.
            - Ajoute directement entre 3 et 5 picks au total pour cette semaine avec add_pick.
            - Si des picks existent déjà, complète seulement jusqu'à un maximum de 5 picks.
            - Chaque raison doit être courte, personnalisée et fondée sur les goûts observés du Maître.
            - Tu peux choisir films et séries, mais reste cohérent avec ses préférences.

            Quand tu as fini, résume brièvement les picks ajoutés pour le Maître.
            """;

    private final WebClient anthropicClient;
    private final WebClient tmdbClient;
    private final MediaService mediaService;
    private final PickService pickService;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String tmdbApiKey;
    private final Cache<Long, List<ChatMessage>> conversationHistory = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public AgentService(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${tmdb.api-key}") String tmdbApiKey,
            MediaService mediaService,
            PickService pickService,
            ObjectMapper objectMapper) {
        this.model = model;
        this.tmdbApiKey = tmdbApiKey;
        this.mediaService = mediaService;
        this.pickService = pickService;
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
        List<ChatMessage> history = conversationHistory.get(userId, k -> new ArrayList<>());
        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        String systemPrompt = buildSystemPrompt();
        String responseText = callClaude(userId, systemPrompt, history);

        history.add(new ChatMessage("assistant", responseText));
        return ChatResponse.builder().message(responseText).suggestedMedia(null).build();
    }

    public void clearHistory(Long userId) {
        conversationHistory.invalidate(userId);
    }

    public List<PickResponse> generatePicks(Long userId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", GENERATE_PICKS_PROMPT));

        String responseText = callClaude(userId, buildSystemPrompt(), messages);
        log.info("Generate picks response for user {}: {}", userId, responseText);

        return pickService.getCurrentPicks(userId);
    }

    private String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    private String callClaude(Long userId, String systemPrompt, List<ChatMessage> messages) {
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

            return processResponse(userId, responseJson, systemPrompt, messages);
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            return "Désolé, je ne peux pas répondre pour le moment. Veuillez réessayer.";
        }
    }

    private String processResponse(Long userId, String responseJson, String systemPrompt, List<ChatMessage> messages) {
        try {
            JsonNode response = objectMapper.readTree(responseJson);
            String stopReason = response.path("stop_reason").asText();
            JsonNode contentArray = response.path("content");

            if ("tool_use".equals(stopReason)) {
                return handleToolUse(userId, contentArray, systemPrompt, messages);
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

    private String handleToolUse(Long userId, JsonNode contentArray, String systemPrompt, List<ChatMessage> messages) {
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

                String result = executeTool(userId, toolName, input);
                toolResults.add(Map.of(
                        "type", "tool_result",
                        "tool_use_id", toolId,
                        "content", result
                ));
            }
        }

        messages.add(new ChatMessage("assistant", assistantContent));
        messages.add(new ChatMessage("user", toolResults));

        return callClaude(userId, systemPrompt, messages);
    }

    private String executeTool(Long userId, String toolName, JsonNode input) {
        try {
            return switch (toolName) {
                // TMDB search tools
                case "search_movie" -> tmdbSearch("/search/movie", input.path("query").asText(),
                        input.has("year") ? input.path("year").asInt() : null);
                case "search_tv" -> tmdbSearch("/search/tv", input.path("query").asText(),
                        input.has("year") ? input.path("year").asInt() : null);
                case "get_trending" -> tmdbTrending(input.path("media_type").asText(), input.path("time_window").asText());
                case "get_recommendations" -> tmdbRecommendations(input.path("tmdb_id").asInt(), input.path("media_type").asText());
                case "discover" -> tmdbDiscover(input);
                // CRUD tools
                case "add_pick" -> addPick(userId, input);
                case "add_to_watched" -> addToWatched(userId, input);
                case "rate_watched" -> rateWatched(userId, input);
                case "comment_watched" -> commentWatched(userId, input);
                case "remove_from_watched" -> removeFromWatched(userId, input);
                case "remove_pick" -> removePick(userId, input);
                case "get_watched_movies" -> getWatchedByType(userId, "MOVIE");
                case "get_watched_series" -> getWatchedByType(userId, "SERIES");
                case "search_watched" -> searchWatched(userId, input.path("query").asText());
                case "get_current_picks" -> getCurrentPicks(userId);
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            log.error("Tool execution failed for {}", toolName, e);
            return "Error: " + e.getMessage();
        }
    }

    // --- CRUD tool implementations ---

    private String addPick(Long userId, JsonNode input) {
        PickRequest request = PickRequest.builder()
                .tmdbId(input.path("tmdb_id").asLong())
                .mediaType(MediaType.valueOf(input.path("media_type").asText().toUpperCase()))
                .title(input.path("title").asText())
                .reason(input.path("reason").asText())
                .build();
        PickResponse response = pickService.addPick(userId, request, true);
        return "Pick ajouté: " + response.getMedia().getTitle() + " (pick_id=" + response.getPickId() + ")";
    }

    private String addToWatched(Long userId, JsonNode input) {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(input.path("tmdb_id").asLong())
                .mediaType(MediaType.valueOf(input.path("media_type").asText().toUpperCase()))
                .title(input.path("title").asText())
                .build();
        WatchedMediaResponse response = mediaService.addToWatched(userId, request);
        return "Ajouté à la liste: " + response.getMedia().getTitle() + " (watched_id=" + response.getWatchedId() + ")";
    }

    private String rateWatched(Long userId, JsonNode input) {
        Long watchedId = input.path("watched_id").asLong();
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(input.path("rating").asInt())
                .build();
        WatchedMediaResponse response = mediaService.updateWatched(userId, watchedId, request);
        return "Note mise à jour: " + response.getMedia().getTitle() + " → " + response.getUserRating() + "/10";
    }

    private String commentWatched(Long userId, JsonNode input) {
        Long watchedId = input.path("watched_id").asLong();
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .comment(input.path("comment").asText())
                .build();
        WatchedMediaResponse response = mediaService.updateWatched(userId, watchedId, request);
        return "Commentaire mis à jour: " + response.getMedia().getTitle();
    }

    private String removeFromWatched(Long userId, JsonNode input) {
        Long watchedId = input.path("watched_id").asLong();
        mediaService.removeFromWatched(userId, watchedId);
        return "Film retiré de la liste (watched_id=" + watchedId + ")";
    }

    private String removePick(Long userId, JsonNode input) {
        Long pickId = input.path("pick_id").asLong();
        pickService.removePick(userId, pickId);
        return "Pick retiré (pick_id=" + pickId + ")";
    }

    private String getWatchedByType(Long userId, String type) {
        try {
            List<WatchedMediaResponse> list = mediaService.getWatchedByType(userId, MediaType.valueOf(type));
            ArrayNode results = objectMapper.createArrayNode();
            for (WatchedMediaResponse w : list) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("watched_id", w.getWatchedId());
                node.put("title", w.getMedia().getTitle());
                node.put("tmdb_id", w.getMedia().getTmdbId());
                if (w.getUserRating() != null) node.put("rating", w.getUserRating());
                if (w.getComment() != null) node.put("comment", w.getComment());
                if (w.getDateWatched() != null) node.put("date_watched", w.getDateWatched());
                results.add(node);
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String getCurrentPicks(Long userId) {
        try {
            List<PickResponse> picks = pickService.getCurrentPicks(userId);
            ArrayNode results = objectMapper.createArrayNode();
            for (PickResponse p : picks) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("pick_id", p.getPickId());
                node.put("title", p.getMedia().getTitle());
                node.put("tmdb_id", p.getMedia().getTmdbId());
                results.add(node);
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String searchWatched(Long userId, String query) {
        try {
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            List<WatchedMediaResponse> movies = mediaService.getWatchedByType(userId, MediaType.MOVIE);
            List<WatchedMediaResponse> series = mediaService.getWatchedByType(userId, MediaType.SERIES);
            ArrayNode results = objectMapper.createArrayNode();

            Stream.concat(movies.stream(), series.stream())
                    .filter(w -> w.getMedia().getTitle() != null
                            && w.getMedia().getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                    .limit(10)
                    .forEach(w -> {
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("watched_id", w.getWatchedId());
                        node.put("title", w.getMedia().getTitle());
                        node.put("tmdb_id", w.getMedia().getTmdbId());
                        node.put("media_type", w.getMedia().getMediaType());
                        if (w.getUserRating() != null) node.put("rating", w.getUserRating());
                        if (w.getComment() != null) node.put("comment", w.getComment());
                        results.add(node);
                    });

            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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

        // CRUD tools
        tools.add(buildTool("add_pick", "Ajouter un film/série dans les Picks de la semaine du Maître",
                Map.of("tmdb_id", prop("integer", "TMDB ID du film ou série"),
                        "media_type", enumProp("string", List.of("MOVIE", "SERIES"), "Type"),
                        "title", prop("string", "Titre du film ou série"),
                        "reason", prop("string", "Raison du pick")),
                List.of("tmdb_id", "media_type", "title", "reason")));

        tools.add(buildTool("add_to_watched", "Ajouter un film/série à la liste des vus du Maître",
                Map.of("tmdb_id", prop("integer", "TMDB ID du film ou série"),
                        "media_type", enumProp("string", List.of("MOVIE", "SERIES"), "Type"),
                        "title", prop("string", "Titre du film ou série")),
                List.of("tmdb_id", "media_type", "title")));

        tools.add(buildTool("rate_watched", "Mettre ou modifier la note d'un film/série déjà vu (0-10)",
                Map.of("watched_id", prop("integer", "ID de l'entrée watched"),
                        "rating", prop("integer", "Note de 0 à 10")),
                List.of("watched_id", "rating")));

        tools.add(buildTool("comment_watched", "Ajouter ou modifier le commentaire d'un film/série déjà vu",
                Map.of("watched_id", prop("integer", "ID de l'entrée watched"),
                        "comment", prop("string", "Commentaire du Maître")),
                List.of("watched_id", "comment")));

        tools.add(buildTool("remove_from_watched", "Retirer un film/série de la liste des vus du Maître",
                Map.of("watched_id", prop("integer", "ID de l'entrée watched")),
                List.of("watched_id")));

        tools.add(buildTool("remove_pick", "Retirer un pick de la semaine",
                Map.of("pick_id", prop("integer", "ID du pick")),
                List.of("pick_id")));

        tools.add(buildTool("get_watched_movies", "Consulter la liste des films vus par le Maître avec notes et commentaires",
                Map.of(), List.of()));

        tools.add(buildTool("get_watched_series", "Consulter la liste des séries vues par le Maître avec notes et commentaires",
                Map.of(), List.of()));

        tools.add(buildTool("search_watched", "Chercher un titre dans la liste des films/séries vus par le Maître",
                Map.of("query", prop("string", "Titre ou mot-clé à chercher")),
                List.of("query")));

        tools.add(buildTool("get_current_picks", "Consulter les picks de la semaine actuelle du Maître",
                Map.of(), List.of()));

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
