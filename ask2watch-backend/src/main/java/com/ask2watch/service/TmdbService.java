package com.ask2watch.service;

import com.ask2watch.config.TmdbConfig;
import com.ask2watch.dto.tmdb.*;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmdbService {

    private final WebClient tmdbWebClient;
    private final TmdbConfig tmdbConfig;

    public TmdbFindResponse findByImdbId(String imdbId) {
        return tmdbWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/find/{imdbId}")
                        .queryParam("external_source", "imdb_id")
                        .queryParam("api_key", tmdbConfig.getApiKey())
                        .build(imdbId))
                .retrieve()
                .bodyToMono(TmdbFindResponse.class)
                .block();
    }

    public TmdbMovieDetails getMovieDetails(int tmdbId) {
        return tmdbWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{tmdbId}")
                        .queryParam("append_to_response", "credits,release_dates")
                        .queryParam("api_key", tmdbConfig.getApiKey())
                        .build(tmdbId))
                .retrieve()
                .bodyToMono(TmdbMovieDetails.class)
                .block();
    }

    public TmdbTvDetails getTvDetails(int tmdbId) {
        return tmdbWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tv/{tmdbId}")
                        .queryParam("append_to_response", "credits,content_ratings")
                        .queryParam("api_key", tmdbConfig.getApiKey())
                        .build(tmdbId))
                .retrieve()
                .bodyToMono(TmdbTvDetails.class)
                .block();
    }

    public List<Media> getTrendingMovies(int limit) {
        try {
            TmdbDiscoverResponse response = tmdbWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/trending/movie/week")
                            .queryParam("api_key", tmdbConfig.getApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(TmdbDiscoverResponse.class)
                    .block();

            if (response == null || response.getResults() == null) {
                return List.of();
            }

            return response.getResults().stream()
                    .limit(limit)
                    .map(this::convertTmdbMovieToMedia)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get trending movies: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Media> discoverMoviesByGenres(List<String> genres, int limit) {
        try {
            // Convert genre names to TMDB genre IDs
            Map<String, Integer> genreIdMap = getTmdbGenreIdMap();
            List<Integer> genreIds = genres.stream()
                    .map(genreIdMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            if (genreIds.isEmpty()) {
                return List.of();
            }

            String genreParam = genreIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("|"));

            TmdbDiscoverResponse response = tmdbWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/discover/movie")
                            .queryParam("with_genres", genreParam)
                            .queryParam("sort_by", "popularity.desc")
                            .queryParam("page", 1)
                            .queryParam("api_key", tmdbConfig.getApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(TmdbDiscoverResponse.class)
                    .block();

            if (response == null || response.getResults() == null) {
                return List.of();
            }

            return response.getResults().stream()
                    .limit(limit)
                    .map(this::convertTmdbMovieToMedia)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to discover movies by genres {}: {}", genres, e.getMessage());
            return List.of();
        }
    }

    private Media convertTmdbMovieToMedia(TmdbMovieResult result) {
        Media media = new Media();
        media.setTmdbId(result.getId());
        media.setTitle(result.getTitle());
        media.setMediaType(MediaType.MOVIE);
        media.setPosterPath(result.getPosterPath());
        media.setSynopsis(result.getOverview());
        if (result.getVoteAverage() > 0) {
            media.setImdbRating(new java.math.BigDecimal(result.getVoteAverage()));
        }
        return media;
    }

    private Map<String, Integer> getTmdbGenreIdMap() {
        return Map.ofEntries(
                Map.entry("Action", 28),
                Map.entry("Adventure", 12),
                Map.entry("Animation", 16),
                Map.entry("Comedy", 35),
                Map.entry("Crime", 80),
                Map.entry("Documentary", 99),
                Map.entry("Drama", 18),
                Map.entry("Fantasy", 14),
                Map.entry("Horror", 27),
                Map.entry("Music", 10402),
                Map.entry("Mystery", 9648),
                Map.entry("Romance", 10749),
                Map.entry("Science Fiction", 878),
                Map.entry("Thriller", 53),
                Map.entry("War", 10752),
                Map.entry("Western", 37)
        );
    }

    public void enrichMedia(Media media) {
        try {
            TmdbFindResponse findResponse = findByImdbId(media.getImdbId());

            if (media.getMediaType() == MediaType.MOVIE) {
                enrichMovie(media, findResponse);
            } else {
                enrichSeries(media, findResponse);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich media '{}' ({}): {}",
                    media.getTitle(), media.getImdbId(), e.getMessage());
        }
    }

    public void enrichMediaByTmdbId(Media media) {
        try {
            if (media.getMediaType() == MediaType.MOVIE) {
                enrichMovieByTmdbId(media);
            } else {
                enrichSeriesByTmdbId(media);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich media '{}' (tmdbId: {}): {}",
                    media.getTitle(), media.getTmdbId(), e.getMessage());
        }
    }

    private void enrichMovieByTmdbId(Media media) {
        TmdbMovieDetails details = getMovieDetails(media.getTmdbId());
        if (details == null) return;

        media.setPosterPath(details.getPosterPath());
        media.setSynopsis(details.getOverview());

        if (details.getGenres() != null && !details.getGenres().isEmpty()) {
            media.setGenres(details.getGenres().stream()
                    .map(TmdbMovieDetails.Genre::getName)
                    .collect(Collectors.joining(", ")));
        }

        if (details.getCredits() != null) {
            media.setStars(extractCast(details.getCredits(), 5));
            media.setDirectors(extractDirectors(details.getCredits()));
        }

        media.setRated(extractMovieCertification(details));
    }

    private void enrichSeriesByTmdbId(Media media) {
        TmdbTvDetails details = getTvDetails(media.getTmdbId());
        if (details == null) return;

        media.setPosterPath(details.getPosterPath());
        media.setSynopsis(details.getOverview());
        media.setSeasons(details.getNumberOfSeasons());

        if (details.getGenres() != null && !details.getGenres().isEmpty()) {
            media.setGenres(details.getGenres().stream()
                    .map(TmdbTvDetails.Genre::getName)
                    .collect(Collectors.joining(", ")));
        }

        if (details.getCredits() != null) {
            media.setStars(extractCast(details.getCredits(), 5));
        }

        if (details.getCreatedBy() != null && !details.getCreatedBy().isEmpty()) {
            media.setDirectors(details.getCreatedBy().stream()
                    .map(TmdbTvDetails.Creator::getName)
                    .collect(Collectors.joining(", ")));
        }

        media.setRated(extractTvCertification(details));
    }

    private void enrichMovie(Media media, TmdbFindResponse findResponse) {
        if (findResponse.getMovieResults() == null || findResponse.getMovieResults().isEmpty()) {
            log.warn("No TMDB movie result for IMDb ID: {}", media.getImdbId());
            return;
        }

        TmdbMovieResult result = findResponse.getMovieResults().get(0);
        media.setTmdbId(result.getId());
        media.setPosterPath(result.getPosterPath());
        media.setSynopsis(result.getOverview());

        TmdbMovieDetails details = getMovieDetails(result.getId());
        if (details == null) return;

        if (details.getCredits() != null) {
            media.setStars(extractCast(details.getCredits(), 5));
            if (media.getDirectors() == null || media.getDirectors().isBlank()) {
                media.setDirectors(extractDirectors(details.getCredits()));
            }
        }

        media.setRated(extractMovieCertification(details));
    }

    private void enrichSeries(Media media, TmdbFindResponse findResponse) {
        if (findResponse.getTvResults() == null || findResponse.getTvResults().isEmpty()) {
            log.warn("No TMDB TV result for IMDb ID: {}", media.getImdbId());
            return;
        }

        TmdbTvResult result = findResponse.getTvResults().get(0);
        media.setTmdbId(result.getId());
        media.setPosterPath(result.getPosterPath());
        media.setSynopsis(result.getOverview());

        TmdbTvDetails details = getTvDetails(result.getId());
        if (details == null) return;

        media.setSeasons(details.getNumberOfSeasons());

        if (details.getCredits() != null) {
            media.setStars(extractCast(details.getCredits(), 5));
        }

        if (details.getCreatedBy() != null && !details.getCreatedBy().isEmpty()) {
            media.setDirectors(details.getCreatedBy().stream()
                    .map(TmdbTvDetails.Creator::getName)
                    .collect(Collectors.joining(", ")));
        }

        media.setRated(extractTvCertification(details));
    }

    private String extractCast(TmdbCredits credits, int limit) {
        if (credits.getCast() == null || credits.getCast().isEmpty()) return null;
        return credits.getCast().stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .limit(limit)
                .map(TmdbCastMember::getName)
                .collect(Collectors.joining(", "));
    }

    private String extractDirectors(TmdbCredits credits) {
        if (credits.getCrew() == null) return null;
        return credits.getCrew().stream()
                .filter(c -> "Director".equals(c.getJob()))
                .map(TmdbCrewMember::getName)
                .collect(Collectors.joining(", "));
    }

    private String extractMovieCertification(TmdbMovieDetails details) {
        if (details.getReleaseDates() == null || details.getReleaseDates().getResults() == null) {
            return null;
        }
        return details.getReleaseDates().getResults().stream()
                .filter(r -> "US".equals(r.getIso31661()))
                .flatMap(r -> r.getReleaseDates().stream())
                .map(TmdbMovieDetails.ReleaseInfo::getCertification)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String extractTvCertification(TmdbTvDetails details) {
        if (details.getContentRatings() == null || details.getContentRatings().getResults() == null) {
            return null;
        }
        return details.getContentRatings().getResults().stream()
                .filter(r -> "US".equals(r.getIso31661()))
                .map(TmdbTvDetails.CountryRating::getRating)
                .filter(r -> r != null && !r.isBlank())
                .findFirst()
                .orElse(null);
    }
}
