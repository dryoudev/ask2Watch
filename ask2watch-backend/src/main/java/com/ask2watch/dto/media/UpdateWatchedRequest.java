package com.ask2watch.dto.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateWatchedRequest {

    @Min(1)
    @Max(5)
    private Integer userRating;

    private String comment;
}
