package com.ask2watch.dto.media;

import com.ask2watch.model.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddWatchedRequest {

    @NotNull(message = "tmdbId is required")
    private Long tmdbId;

    @NotNull(message = "mediaType is required")
    private MediaType mediaType;

    @NotBlank(message = "title is required")
    private String title;
}
