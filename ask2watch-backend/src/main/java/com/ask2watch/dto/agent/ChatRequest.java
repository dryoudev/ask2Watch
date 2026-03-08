package com.ask2watch.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequest {

    @NotBlank(message = "message is required")
    @Size(max = 2000, message = "message must not exceed 2000 characters")
    private String message;
}
