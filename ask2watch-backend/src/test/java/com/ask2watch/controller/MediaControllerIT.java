package com.ask2watch.controller;

import com.ask2watch.AbstractIntegrationTest;
import com.ask2watch.dto.media.AddWatchedRequest;
import com.ask2watch.dto.media.UpdateWatchedRequest;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.User;
import com.ask2watch.model.UserWatched;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaControllerIT extends AbstractIntegrationTest {

    private User user1;
    private User user2;
    private Media movieMedia;
    private Media seriesMedia;
    private UserWatched watched1;
    private UserWatched watched2;

    @BeforeEach
    void setUp() {
        user1 = createUser("alice", "alice@test.com", "pass123");
        user2 = createUser("bob", "bob@test.com", "pass123");
        movieMedia = createMedia("Inception", MediaType.MOVIE, 27205);
        seriesMedia = createMedia("Breaking Bad", MediaType.SERIES, 1396);
        watched1 = createWatched(user1, movieMedia, 4, "Excellent film");
        watched2 = createWatched(user1, seriesMedia, null, null);
    }

    // --- GET /api/media/watched?type=MOVIE ---

    @Test
    void testGetWatched_movies_success() throws Exception {
        mockMvc.perform(get("/api/media/watched?type=MOVIE")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].watchedId").value(watched1.getId().intValue()))
                .andExpect(jsonPath("$[0].media.title").value("Inception"))
                .andExpect(jsonPath("$[0].media.mediaType").value("MOVIE"))
                .andExpect(jsonPath("$[0].userRating").value(4))
                .andExpect(jsonPath("$[0].comment").value("Excellent film"));
    }

    @Test
    void testGetWatched_series_success() throws Exception {
        mockMvc.perform(get("/api/media/watched?type=SERIES")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].media.title").value("Breaking Bad"))
                .andExpect(jsonPath("$[0].media.mediaType").value("SERIES"));
    }

    @Test
    void testGetWatched_empty_whenUserHasNone() throws Exception {
        mockMvc.perform(get("/api/media/watched?type=MOVIE")
                        .header("Authorization", authHeader(user2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetWatched_fail_noAuth() throws Exception {
        var result = mockMvc.perform(get("/api/media/watched?type=MOVIE")).andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "Expected 401 or 403, got " + status);
    }

    @Test
    void testGetWatched_fail_invalidToken() throws Exception {
        var result = mockMvc.perform(get("/api/media/watched?type=MOVIE")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    @Test
    void testGetWatched_fail_invalidType() throws Exception {
        mockMvc.perform(get("/api/media/watched?type=INVALID")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/media/watched ---

    @Test
    void testAddToWatched_newMedia_success() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(550L)
                .mediaType(MediaType.MOVIE)
                .title("Fight Club")
                .build();

        mockMvc.perform(post("/api/media/watched")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchedId").value(notNullValue()))
                .andExpect(jsonPath("$.media.title").value("Fight Club"))
                .andExpect(jsonPath("$.media.tmdbId").value(550))
                .andExpect(jsonPath("$.media.mediaType").value("MOVIE"));

        // Verify media created and user watched created for user1
        assertTrue(mediaRepository.findAll().stream()
                .anyMatch(m -> m.getTmdbId() != null && m.getTmdbId() == 550));
        // Count only watched entries for user1
        long user1WatchedCount = userWatchedRepository.findAll().stream()
                .filter(w -> w.getUser().getId().equals(user1.getId()))
                .count();
        assertEquals(3, user1WatchedCount); // watched1, watched2, new
    }

    @Test
    void testAddToWatched_existingMedia_success() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(27205L)
                .mediaType(MediaType.MOVIE)
                .title("Inception")
                .build();

        long mediaCountBefore = mediaRepository.count();

        mockMvc.perform(post("/api/media/watched")
                        .header("Authorization", authHeader(user2))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.id").value(movieMedia.getId().intValue()));

        // Verify no duplicate media
        assertEquals(mediaCountBefore, mediaRepository.count());
    }

    @Test
    void testAddToWatched_fail_tmdbIdNull() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .mediaType(MediaType.MOVIE)
                .title("Fight Club")
                .build();

        mockMvc.perform(post("/api/media/watched")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddToWatched_fail_mediaTypeNull() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        mockMvc.perform(post("/api/media/watched")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddToWatched_fail_titleBlank() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(550L)
                .mediaType(MediaType.MOVIE)
                .title("")
                .build();

        mockMvc.perform(post("/api/media/watched")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddToWatched_fail_noAuth() throws Exception {
        AddWatchedRequest request = AddWatchedRequest.builder()
                .tmdbId(550L)
                .mediaType(MediaType.MOVIE)
                .title("Fight Club")
                .build();

        var result = mockMvc.perform(post("/api/media/watched")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- PUT /api/media/watched/{id} ---

    @Test
    void testUpdateWatched_rating_success() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(5)
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRating").value(5))
                .andExpect(jsonPath("$.comment").value("Excellent film"));

        UserWatched updated = userWatchedRepository.findById(watched1.getId()).orElseThrow();
        assertEquals(5, updated.getUserRating());
    }

    @Test
    void testUpdateWatched_comment_success() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .comment("Chef-d'oeuvre absolu")
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Chef-d'oeuvre absolu"))
                .andExpect(jsonPath("$.userRating").value(4));
    }

    @Test
    void testUpdateWatched_ratingAndComment_success() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(3)
                .comment("Revu, moins bien")
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRating").value(3))
                .andExpect(jsonPath("$.comment").value("Revu, moins bien"));
    }

    @Test
    void testUpdateWatched_fail_ratingAboveMax() throws Exception {
        // BUG DOCUMENTED: @Max(5) should be @Max(10) to match MCP tool (1-10 range)
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(10)
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateWatched_fail_ratingBelowMin() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(0)
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateWatched_fail_wrongUser() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(1)
                .build();

        mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user2))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        // Verify not modified
        UserWatched unchanged = userWatchedRepository.findById(watched1.getId()).orElseThrow();
        assertEquals(4, unchanged.getUserRating());
    }

    @Test
    void testUpdateWatched_fail_notFound() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(3)
                .build();

        mockMvc.perform(put("/api/media/watched/99999")
                        .header("Authorization", authHeader(user1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testUpdateWatched_fail_noAuth() throws Exception {
        UpdateWatchedRequest request = UpdateWatchedRequest.builder()
                .userRating(3)
                .build();

        var result = mockMvc.perform(put("/api/media/watched/" + watched1.getId())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- DELETE /api/media/watched/{id} ---

    @Test
    void testRemoveFromWatched_success() throws Exception {
        mockMvc.perform(delete("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isNoContent());

        // Verify deleted
        assertFalse(userWatchedRepository.findById(watched1.getId()).isPresent());
        // Verify media still exists
        assertTrue(mediaRepository.findById(movieMedia.getId()).isPresent());
    }

    @Test
    void testRemoveFromWatched_fail_wrongUser() throws Exception {
        mockMvc.perform(delete("/api/media/watched/" + watched1.getId())
                        .header("Authorization", authHeader(user2)))
                .andExpect(status().isInternalServerError());

        // Verify not deleted
        assertTrue(userWatchedRepository.findById(watched1.getId()).isPresent());
    }

    @Test
    void testRemoveFromWatched_fail_notFound() throws Exception {
        mockMvc.perform(delete("/api/media/watched/99999")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testRemoveFromWatched_fail_noAuth() throws Exception {
        var result = mockMvc.perform(delete("/api/media/watched/" + watched1.getId()))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }

    // --- GET /api/media/{id} ---

    @Test
    void testGetMedia_success() throws Exception {
        mockMvc.perform(get("/api/media/" + movieMedia.getId())
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(movieMedia.getId().intValue()))
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.tmdbId").value(27205))
                .andExpect(jsonPath("$.mediaType").value("MOVIE"));
    }

    @Test
    void testGetMedia_fail_notFound() throws Exception {
        mockMvc.perform(get("/api/media/99999")
                        .header("Authorization", authHeader(user1)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetMedia_fail_noAuth() throws Exception {
        var result = mockMvc.perform(get("/api/media/" + movieMedia.getId()))
                .andReturn();
        assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403);
    }
}
