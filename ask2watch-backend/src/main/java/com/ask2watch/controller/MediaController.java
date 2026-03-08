package com.ask2watch.controller;

import com.ask2watch.dto.media.AddWatchedRequest;
import com.ask2watch.dto.media.MediaResponse;
import com.ask2watch.dto.media.UpdateWatchedRequest;
import com.ask2watch.dto.media.WatchedMediaResponse;
import com.ask2watch.model.MediaType;
import com.ask2watch.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/watched")
    public ResponseEntity<List<WatchedMediaResponse>> getWatched(
            @RequestParam String type,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        MediaType mediaType = MediaType.valueOf(type);
        return ResponseEntity.ok(mediaService.getWatchedByType(userId, mediaType));
    }

    @PutMapping("/watched/{id}")
    public ResponseEntity<WatchedMediaResponse> updateWatched(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWatchedRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(mediaService.updateWatched(userId, id, request));
    }

    @PostMapping("/watched")
    public ResponseEntity<WatchedMediaResponse> addToWatched(
            @Valid @RequestBody AddWatchedRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(mediaService.addToWatched(userId, request));
    }

    @DeleteMapping("/watched/{id}")
    public ResponseEntity<Void> removeFromWatched(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        mediaService.removeFromWatched(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaResponse> getMedia(@PathVariable Long id) {
        return ResponseEntity.ok(mediaService.getMediaById(id));
    }
}
