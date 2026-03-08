package com.ask2watch.service;

import com.ask2watch.dto.mapper.MediaMapper;
import com.ask2watch.dto.media.AddWatchedRequest;
import com.ask2watch.dto.media.MediaResponse;
import com.ask2watch.dto.media.UpdateWatchedRequest;
import com.ask2watch.dto.media.WatchedMediaResponse;
import com.ask2watch.exception.ResourceNotFoundException;
import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import com.ask2watch.model.User;
import com.ask2watch.model.UserWatched;
import com.ask2watch.repository.MediaRepository;
import com.ask2watch.repository.UserRepository;
import com.ask2watch.repository.UserWatchedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaRepository mediaRepository;
    private final UserWatchedRepository userWatchedRepository;
    private final UserRepository userRepository;

    public List<WatchedMediaResponse> getWatchedByType(Long userId, MediaType type) {
        return userWatchedRepository.findByUserIdAndMedia_MediaType(userId, type).stream()
                .map(MediaMapper::toWatchedMediaResponse)
                .toList();
    }

    public WatchedMediaResponse updateWatched(Long userId, Long watchedId, UpdateWatchedRequest request) {
        UserWatched watched = userWatchedRepository.findById(watchedId)
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Watched entry not found"));

        if (request.getUserRating() != null) {
            watched.setUserRating(request.getUserRating());
        }
        if (request.getComment() != null) {
            watched.setComment(request.getComment());
        }

        watched = userWatchedRepository.save(watched);
        return MediaMapper.toWatchedMediaResponse(watched);
    }

    public MediaResponse getMediaById(Long id) {
        return mediaRepository.findById(id)
                .map(MediaMapper::toMediaResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
    }

    public WatchedMediaResponse addToWatched(Long userId, AddWatchedRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Find or create media by TMDB ID
        Media media = mediaRepository.findByTmdbId(Math.toIntExact(request.getTmdbId()))
                .orElseGet(() -> {
                    Media newMedia = new Media();
                    newMedia.setTmdbId(Math.toIntExact(request.getTmdbId()));
                    newMedia.setTitle(request.getTitle());
                    newMedia.setMediaType(request.getMediaType());
                    return mediaRepository.save(newMedia);
                });

        // Create UserWatched entry
        UserWatched watched = new UserWatched();
        watched.setUser(user);
        watched.setMedia(media);

        watched = userWatchedRepository.save(watched);
        return MediaMapper.toWatchedMediaResponse(watched);
    }

    public void removeFromWatched(Long userId, Long watchedId) {
        UserWatched watched = userWatchedRepository.findById(watchedId)
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Watched entry not found"));

        userWatchedRepository.delete(watched);
    }
}
