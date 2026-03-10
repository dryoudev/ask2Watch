package com.ask2watch.dto.recommendation;

import com.ask2watch.dto.media.MediaResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendationDto {

    private MediaResponse media;
    private String source;
    private String reason;
}
