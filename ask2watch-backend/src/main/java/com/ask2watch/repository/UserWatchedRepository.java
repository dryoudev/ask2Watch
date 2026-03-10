package com.ask2watch.repository;

import com.ask2watch.model.UserWatched;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserWatchedRepository extends JpaRepository<UserWatched, Long> {

    @Query(value = """
            SELECT uw.*
            FROM user_watched uw
            JOIN media m ON m.id = uw.media_id
            WHERE uw.user_id = :userId
              AND m.media_type::text = :mediaType
            ORDER BY uw.date_watched DESC NULLS LAST
            """, nativeQuery = true)
    List<UserWatched> findByUserIdAndMediaType(@Param("userId") Long userId, @Param("mediaType") String mediaType);

    Optional<UserWatched> findByUserIdAndMediaId(Long userId, Long mediaId);

    List<UserWatched> findByUserId(Long userId);
}
