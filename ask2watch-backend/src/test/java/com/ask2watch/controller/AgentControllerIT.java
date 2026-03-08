package com.ask2watch.controller;

import com.ask2watch.AbstractIntegrationTest;
import com.ask2watch.dto.agent.ChatRequest;
import com.ask2watch.dto.agent.ChatResponse;
import com.ask2watch.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerIT extends AbstractIntegrationTest {

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = createUser("alice", "alice@test.com", "pass123");
        user2 = createUser("bob", "bob@test.com", "pass123");
    }

    // --- POST /api/agent/chat ---

    @Test
    void testChat_success() throws Exception {
        when(agentService.chat(user1.getId(), "Recommande moi un film"))
                .thenReturn(ChatResponse.builder()
                        .message("Je recommande Inception")
                        .suggestedMedia(List.of())
                        .build());

        ChatRequest request = ChatRequest.builder()
                .message("Recommande moi un film")
                .build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Je recommande Inception"));

        verify(agentService).chat(user1.getId(), "Recommande moi un film");
    }

    @Test
    void testChat_emptyMessage_success() throws Exception {
        when(agentService.chat(any(), eq("")))
                .thenReturn(ChatResponse.builder().message("Réponse vide").build());

        ChatRequest request = ChatRequest.builder()
                .message("")
                .build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testChat_nullMessage_success() throws Exception {
        when(agentService.chat(any(), any()))
                .thenReturn(ChatResponse.builder().message("Réponse").build());

        ChatRequest request = ChatRequest.builder().build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testChat_isolation_userId() throws Exception {
        when(agentService.chat(eq(user2.getId()), any()))
                .thenReturn(ChatResponse.builder().message("Réponse user2").build());

        ChatRequest request = ChatRequest.builder()
                .message("Test message")
                .build();

        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", authHeader(user2))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(agentService).chat(eq(user2.getId()), any());
    }

    @Test
    void testChat_fail_noAuth() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("Test")
                .build();

        var result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- DELETE /api/agent/history ---

    @Test
    void testClearHistory_success() throws Exception {
        mockMvc.perform(delete("/api/agent/history")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isNoContent());

        verify(agentService).clearHistory(eq(user1.getId()));
    }

    @Test
    void testClearHistory_isolation_userId() throws Exception {
        mockMvc.perform(delete("/api/agent/history")
                        .header("Authorization", authHeader(user2)))
                .andExpect(status().isNoContent());

        verify(agentService).clearHistory(eq(user2.getId()));
    }

    @Test
    void testClearHistory_fail_noAuth() throws Exception {
        var result = mockMvc.perform(delete("/api/agent/history"))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }
}
