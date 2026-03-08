package com.ask2watch.controller;

import com.ask2watch.dto.media.PickRequest;
import com.ask2watch.dto.media.PickResponse;
import com.ask2watch.service.PickService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/picks")
@RequiredArgsConstructor
public class PickController {

    private final PickService pickService;

    @GetMapping("/current")
    public ResponseEntity<List<PickResponse>> getCurrentPicks(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pickService.getCurrentPicks(userId));
    }

    @GetMapping
    public ResponseEntity<List<PickResponse>> getAllPicks(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pickService.getAllPicks(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PickResponse>> getPicksHistory(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pickService.getPicksHistory(userId, limit));
    }

    @PostMapping
    public ResponseEntity<PickResponse> addPick(
            @Valid @RequestBody PickRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pickService.addPick(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removePick(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        pickService.removePick(userId, id);
        return ResponseEntity.noContent().build();
    }
}
