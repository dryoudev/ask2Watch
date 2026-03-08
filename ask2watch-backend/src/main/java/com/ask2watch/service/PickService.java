package com.ask2watch.service;

import com.ask2watch.dto.mapper.MediaMapper;
import com.ask2watch.dto.media.PickRequest;
import com.ask2watch.dto.media.PickResponse;
import com.ask2watch.exception.ResourceNotFoundException;
import com.ask2watch.model.Media;
import com.ask2watch.model.PickOfWeek;
import com.ask2watch.model.User;
import com.ask2watch.repository.MediaRepository;
import com.ask2watch.repository.PickOfWeekRepository;
import com.ask2watch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PickService {

    private final PickOfWeekRepository pickOfWeekRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final TmdbService tmdbService;

    public List<PickResponse> getCurrentPicks(Long userId) {
        LocalDate startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return pickOfWeekRepository.findByUserIdAndWeekDate(userId, startOfWeek).stream()
                .map(MediaMapper::toPickResponse)
                .toList();
    }

    public List<PickResponse> getAllPicks(Long userId) {
        return pickOfWeekRepository.findByUserId(userId).stream()
                .map(MediaMapper::toPickResponse)
                .toList();
    }

    public List<PickResponse> getPicksHistory(Long userId, int limit) {
        return pickOfWeekRepository.findByUserId(userId).stream()
                .sorted((a, b) -> b.getWeekDate().compareTo(a.getWeekDate()))
                .limit(limit)
                .map(MediaMapper::toPickResponse)
                .toList();
    }

    public PickResponse addPick(Long userId, PickRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Find or create media by TMDB ID
        Media media = mediaRepository.findByTmdbId(Math.toIntExact(request.getTmdbId()))
                .map(existing -> {
                    // Enrich existing media if missing data
                    if (existing.getPosterPath() == null || existing.getSynopsis() == null) {
                        tmdbService.enrichMediaByTmdbId(existing);
                        return mediaRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Media newMedia = new Media();
                    newMedia.setTmdbId(Math.toIntExact(request.getTmdbId()));
                    newMedia.setTitle(request.getTitle());
                    newMedia.setMediaType(request.getMediaType());
                    // Enrich with complete TMDB data
                    tmdbService.enrichMediaByTmdbId(newMedia);
                    return mediaRepository.save(newMedia);
                });

        // Create pick for current week
        LocalDate startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        PickOfWeek pick = new PickOfWeek();
        pick.setUser(user);
        pick.setMedia(media);
        pick.setWeekDate(startOfWeek);
        pick.setCreatedByAgent(false);

        pick = pickOfWeekRepository.save(pick);
        return MediaMapper.toPickResponse(pick);
    }

    public void removePick(Long userId, Long pickId) {
        PickOfWeek pick = pickOfWeekRepository.findById(pickId)
                .filter(p -> p.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Pick not found"));

        pickOfWeekRepository.delete(pick);
    }
}
