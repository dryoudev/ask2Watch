package com.ask2watch.repository;

import com.ask2watch.model.Media;
import com.ask2watch.model.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findByImdbId(String imdbId);

    Optional<Media> findByTmdbId(Integer tmdbId);

    List<Media> findByMediaType(MediaType mediaType);
}
