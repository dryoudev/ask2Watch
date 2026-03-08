package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WatchedMediaResponse {

    private Long watchedId;
    private MediaResponse media;
    private Integer userRating;
    private String dateWatched;
    private String comment;
}
