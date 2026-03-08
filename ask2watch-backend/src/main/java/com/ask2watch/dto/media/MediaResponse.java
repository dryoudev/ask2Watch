package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaResponse {

    private Long id;
    private String imdbId;
    private Integer tmdbId;
    private String title;
    private String mediaType;
    private String year;
    private Integer runtimeMins;
    private String genres;
    private Double imdbRating;
    private String directors;
    private String stars;
    private String synopsis;
    private String rated;
    private String posterPath;
    private Integer seasons;
}
