package com.ask2watch.controller;

import com.ask2watch.AbstractIntegrationTest;
import com.ask2watch.dto.media.PickRequest;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.PickOfWeek;
import com.ask2watch.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PickControllerIT extends AbstractIntegrationTest {

    private User user1;
    private User user2;
    private Media media1;
    private Media media2;
    private Media media3;
    private LocalDate currentMonday;
    private PickOfWeek pick1;
    private PickOfWeek pick2;
    private PickOfWeek pick3;

    @BeforeEach
    void setUp() {
        user1 = createUser("alice", "alice@test.com", "pass123");
        user2 = createUser("bob", "bob@test.com", "pass123");
        media1 = createMedia("Inception", MediaType.MOVIE, 27205);
        media2 = createMedia("Breaking Bad", MediaType.SERIES, 1396);
        media3 = createMedia("The Matrix", MediaType.MOVIE, 603);

        currentMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        pick1 = createPick(user1, media1, currentMonday);
        pick2 = createPick(user1, media2, currentMonday);
        pick3 = createPick(user1, media3, currentMonday.minusWeeks(1));
    }

    // --- GET /api/picks/current ---

    @Test
    void testGetCurrentPicks_success() throws Exception {
        mockMvc.perform(get("/api/picks/current")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].media.title").value(org.hamcrest.Matchers.containsInAnyOrder("Inception", "Breaking Bad")))
                .andExpect(jsonPath("$[0].weekDate").value(currentMonday.toString()))
                .andExpect(jsonPath("$[0].createdByAgent").value(false))
                .andExpect(jsonPath("$[1].weekDate").value(currentMonday.toString()))
                .andExpect(jsonPath("$[1].createdByAgent").value(false));
    }

    @Test
    void testGetCurrentPicks_isolation_otherUserSeesEmpty() throws Exception {
        mockMvc.perform(get("/api/picks/current")
                        .header("Authorization", authHeader(user2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetCurrentPicks_fail_noAuth() throws Exception {
        var result = mockMvc.perform(get("/api/picks/current"))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- GET /api/picks ---

    @Test
    void testGetAllPicks_success() throws Exception {
        mockMvc.perform(get("/api/picks")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void testGetAllPicks_fail_noAuth() throws Exception {
        var result = mockMvc.perform(get("/api/picks"))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- GET /api/picks/history ---

    @Test
    void testGetPicksHistory_defaultLimit() throws Exception {
        mockMvc.perform(get("/api/picks/history")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].weekDate").value(currentMonday.toString()))
                .andExpect(jsonPath("$[2].weekDate").value(currentMonday.minusWeeks(1).toString()));
    }

    @Test
    void testGetPicksHistory_customLimit() throws Exception {
        mockMvc.perform(get("/api/picks/history?limit=2")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void testGetPicksHistory_fail_noAuth() throws Exception {
        var result = mockMvc.perform(get("/api/picks/history"))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- POST /api/picks ---

    @Test
    void testAddPick_newMedia_success() throws Exception {
        PickRequest request = PickRequest.builder()
                .tmdbId(680L)
                .mediaType(MediaType.MOVIE)
                .title("Pulp Fiction")
                .reason("Chef-d'oeuvre de Tarantino")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickId").value(notNullValue()))
                .andExpect(jsonPath("$.media.title").value("Pulp Fiction"))
                .andExpect(jsonPath("$.media.tmdbId").value(680))
                .andExpect(jsonPath("$.weekDate").value(currentMonday.toString()))
                .andExpect(jsonPath("$.createdByAgent").value(false));

        // Verify media and pick created
        assertTrue(mediaRepository.findAll().stream()
                .anyMatch(m -> m.getTmdbId() == 680));
        assertEquals(4, pickOfWeekRepository.findAll().size());
    }

    @Test
    void testAddPick_existingMedia_success() throws Exception {
        long mediaCountBefore = mediaRepository.count();

        PickRequest request = PickRequest.builder()
                .tmdbId(27205L)
                .mediaType(MediaType.MOVIE)
                .title("Inception")
                .reason("Mon préféré")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify no duplicate media
        assertEquals(mediaCountBefore, mediaRepository.count());
    }

    @Test
    void testAddPick_fail_tmdbIdNull() throws Exception {
        PickRequest request = PickRequest.builder()
                .mediaType(MediaType.MOVIE)
                .title("Pulp Fiction")
                .reason("Top")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddPick_fail_titleBlank() throws Exception {
        PickRequest request = PickRequest.builder()
                .tmdbId(680L)
                .mediaType(MediaType.MOVIE)
                .title("")
                .reason("Top")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddPick_fail_reasonBlank() throws Exception {
        PickRequest request = PickRequest.builder()
                .tmdbId(680L)
                .mediaType(MediaType.MOVIE)
                .title("Pulp Fiction")
                .reason("")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddPick_fail_mediaTypeNull() throws Exception {
        PickRequest request = PickRequest.builder()
                .tmdbId(680L)
                .title("Pulp Fiction")
                .reason("Top")
                .build();

        mockMvc.perform(post("/api/picks")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddPick_fail_noAuth() throws Exception {
        PickRequest request = PickRequest.builder()
                .tmdbId(680L)
                .mediaType(MediaType.MOVIE)
                .title("Pulp Fiction")
                .reason("Top")
                .build();

        var result = mockMvc.perform(post("/api/picks")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- DELETE /api/picks/{id} ---

    @Test
    void testRemovePick_success() throws Exception {
        mockMvc.perform(delete("/api/picks/" + pick1.getId())
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isNoContent());

        // Verify deleted
        assertFalse(pickOfWeekRepository.findById(pick1.getId()).isPresent());
        // Verify media still exists
        assertTrue(mediaRepository.findById(media1.getId()).isPresent());
    }

    @Test
    void testRemovePick_fail_wrongUser() throws Exception {
        mockMvc.perform(delete("/api/picks/" + pick1.getId())
                        .header("Authorization", authHeader(user2)))
                .andExpect(status().isInternalServerError());

        // Verify not deleted
        assertTrue(pickOfWeekRepository.findById(pick1.getId()).isPresent());
    }

    @Test
    void testRemovePick_fail_notFound() throws Exception {
        mockMvc.perform(delete("/api/picks/99999")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testRemovePick_fail_noAuth() throws Exception {
        var result = mockMvc.perform(delete("/api/picks/" + pick1.getId()))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }
}
