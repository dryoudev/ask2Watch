package com.ask2watch.dto.agent;

import com.ask2watch.dto.media.MediaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String message;
    private List<MediaResponse> suggestedMedia;
}
