package com.ask2watch.repository;

import com.ask2watch.model.MediaType;
import com.ask2watch.model.UserWatched;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWatchedRepository extends JpaRepository<UserWatched, Long> {

    List<UserWatched> findByUserIdAndMedia_MediaType(Long userId, MediaType mediaType);

    Optional<UserWatched> findByUserIdAndMediaId(Long userId, Long mediaId);

    List<UserWatched> findByUserId(Long userId);
}
