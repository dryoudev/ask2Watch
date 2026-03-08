package com.ask2watch.dto.mapper;

import com.ask2watch.dto.media.MediaResponse;
import com.ask2watch.dto.media.PickResponse;
import com.ask2watch.dto.media.WatchedMediaResponse;
import com.ask2watch.model.Media;
import com.ask2watch.model.PickOfWeek;
import com.ask2watch.model.UserWatched;

public final class MediaMapper {

    private MediaMapper() {}

    public static MediaResponse toMediaResponse(Media media) {
        return MediaResponse.builder()
                .id(media.getId())
                .imdbId(media.getImdbId())
                .tmdbId(media.getTmdbId())
                .title(media.getTitle())
                .mediaType(media.getMediaType().name())
                .year(media.getYear())
                .runtimeMins(media.getRuntimeMins())
                .genres(media.getGenres())
                .imdbRating(media.getImdbRating() != null ? media.getImdbRating().doubleValue() : null)
                .directors(media.getDirectors())
                .stars(media.getStars())
                .synopsis(media.getSynopsis())
                .rated(media.getRated())
                .posterPath(media.getPosterPath())
                .seasons(media.getSeasons())
                .build();
    }

    public static WatchedMediaResponse toWatchedMediaResponse(UserWatched watched) {
        return WatchedMediaResponse.builder()
                .watchedId(watched.getId())
                .media(toMediaResponse(watched.getMedia()))
                .userRating(watched.getUserRating())
                .dateWatched(watched.getDateWatched() != null ? watched.getDateWatched().toString() : null)
                .comment(watched.getComment())
                .build();
    }

    public static PickResponse toPickResponse(PickOfWeek pick) {
        return PickResponse.builder()
                .pickId(pick.getId())
                .media(toMediaResponse(pick.getMedia()))
                .weekDate(pick.getWeekDate().toString())
                .createdByAgent(Boolean.TRUE.equals(pick.getCreatedByAgent()))
                .build();
    }
}
