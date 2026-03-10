package com.ask2watch.controller;

import com.ask2watch.dto.agent.ChatRequest;
import com.ask2watch.dto.agent.ChatResponse;
import com.ask2watch.dto.media.PickResponse;
import com.ask2watch.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return agentService.chat(userId, request.getMessage());
    }

    @PostMapping("/generate-picks")
    public List<PickResponse> generatePicks(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return agentService.generatePicks(userId);
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        agentService.clearHistory(userId);
        return ResponseEntity.noContent().build();
    }
}
