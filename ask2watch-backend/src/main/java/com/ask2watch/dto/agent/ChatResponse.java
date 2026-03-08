package com.ask2watch.dto.agent;

import com.ask2watch.dto.media.MediaResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String message;
    private List<MediaResponse> suggestedMedia;
}
