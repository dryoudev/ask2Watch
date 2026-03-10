package com.ask2watch.service.recommendation;

import com.ask2watch.dto.mapper.MediaMapper;
import com.ask2watch.dto.recommendation.RecommendationDto;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.UserWatched;
import com.ask2watch.repository.UserWatchedRepository;
import com.ask2watch.service.TmdbService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TmdbRecommendationProvider implements RecommendationProvider {

    private final UserWatchedRepository userWatchedRepository;
    private final TmdbService tmdbService;

    @Override
    public List<RecommendationDto> getRecommendations(Long userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<UserWatched> watchedEntries = userWatchedRepository.findByUserId(userId);
        Set<Integer> watchedTmdbIds = watchedEntries.stream()
                .map(UserWatched::getMedia)
                .map(Media::getTmdbId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> extractedGenres = watchedEntries.stream()
                .sorted(Comparator.comparing((UserWatched watched) -> watched.getUserRating() == null ? 0 : watched.getUserRating())
                        .reversed())
                .map(UserWatched::getMedia)
                .filter(media -> media.getMediaType() == MediaType.MOVIE)
                .map(Media::getGenres)
                .filter(genres -> genres != null && !genres.isBlank())
                .flatMap(genres -> List.of(genres.split(",")).stream())
                .map(String::trim)
                .filter(genre -> !genre.isBlank())
                .map(this::normalizeGenre)
                .distinct()
                .limit(3)
                .toList();

        List<String> favoriteGenres = extractedGenres.isEmpty() ? List.of("Drama", "Thriller") : extractedGenres;

        LinkedHashSet<Media> recommendations = new LinkedHashSet<>(tmdbService.discoverMoviesByGenres(favoriteGenres, limit * 3));

        return recommendations.stream()
                .filter(media -> media.getTmdbId() != null)
                .filter(media -> !watchedTmdbIds.contains(media.getTmdbId()))
                .limit(limit)
                .map(media -> RecommendationDto.builder()
                        .media(MediaMapper.toMediaResponse(media))
                        .source("Films Similaires")
                        .reason("Basé sur vos genres favoris : " + String.join(", ", favoriteGenres))
                        .build())
                .toList();
    }

    private String normalizeGenre(String genre) {
        String lower = genre.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "sci-fi", "science fiction" -> "Science Fiction";
            default -> Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        };
    }
}
