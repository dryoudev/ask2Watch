package com.ask2watch.controller;

import com.ask2watch.AbstractIntegrationTest;
import com.ask2watch.dto.auth.LoginRequest;
import com.ask2watch.dto.auth.RegisterRequest;
import com.ask2watch.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    // --- POST /api/auth/register ---

    @Test
    void testRegister_success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(matchesPattern(".*\\..*\\..*"))) // JWT format: 3 parts
                .andExpect(jsonPath("$.username").value("testuser"));

        // Verify user exists in database with hashed password
        User savedUser = userRepository.findByEmail("test@example.com").orElseThrow();
        assertEquals("testuser", savedUser.getUsername());
        assertNotEquals("password123", savedUser.getPasswordHash()); // Should be hashed
    }

    @Test
    void testRegister_fail_emailAlreadyUsed() throws Exception {
        // Precondition: User already exists
        createUser("existing", "duplicate@example.com", "password");

        RegisterRequest request = RegisterRequest.builder()
                .username("other")
                .email("duplicate@example.com")
                .password("password123")
                .build();

        // Service throws RuntimeException which results in 500 error
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // With no exception handler, expect 500 Internal Server Error
        assertEquals(500, result.getResponse().getStatus());

        // Verify only one user with this email
        assertEquals(1, userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("duplicate@example.com"))
                .count());
    }

    @Test
    void testRegister_fail_usernameBlank() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("")
                .email("blank-user@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify no user created with this email
        assertEquals(0, userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("blank-user@example.com"))
                .count());
    }

    @Test
    void testRegister_fail_emailInvalid() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("notanemail")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegister_fail_passwordTooShort() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("abc")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegister_fail_missingBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/auth/login ---

    @Test
    void testLogin_success() throws Exception {
        // Precondition: User exists
        createUser("testuser", "test@example.com", "password123");

        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(matchesPattern(".*\\..*\\..*")))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andReturn();

        // Extract token and verify validity
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        assertEquals("test@example.com", jwtService.extractEmail(token));
        assertEquals(true, jwtService.isTokenValid(token));
    }

    @Test
    void testLogin_fail_wrongPassword() throws Exception {
        createUser("testuser", "wrong-pwd@example.com", "password123");

        LoginRequest request = LoginRequest.builder()
                .email("wrong-pwd@example.com")
                .password("wrongpassword")
                .build();

        // Service throws RuntimeException which results in 500 error
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // With no exception handler, expect 500 Internal Server Error
        assertEquals(500, result.getResponse().getStatus());
    }

    @Test
    void testLogin_fail_emailNotFound() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("password123")
                .build();

        // Service throws RuntimeException which results in 500 error
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // With no exception handler, expect 500 Internal Server Error
        assertEquals(500, result.getResponse().getStatus());
    }

    @Test
    void testLogin_fail_emailBlank() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_fail_noAuth_accesses_securedEndpoint() throws Exception {
        // Without authentication, should get either 401 Unauthorized or 403 Forbidden
        mockMvc.perform(get("/api/media/watched?type=MOVIE"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403, "Expected 401 or 403, got " + status);
                });
    }
}
