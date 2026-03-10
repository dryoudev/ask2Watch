package com.ask2watch.model;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imdb_id", unique = true, length = 20)
    private String imdbId;

    @Column(name = "tmdb_id", unique = true)
    private Integer tmdbId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "original_title", length = 500)
    private String originalTitle;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::media_type_enum")
    @Column(name = "media_type", nullable = false, columnDefinition = "media_type_enum")
    private MediaType mediaType;

    @Column(length = 20)
    private String year;

    @Column(name = "runtime_mins")
    private Integer runtimeMins;

    @Column(length = 500)
    private String genres;

    @Column(name = "imdb_rating", precision = 3, scale = 1)
    private BigDecimal imdbRating;

    @Column(name = "num_votes")
    private Integer numVotes;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(length = 500)
    private String directors;

    @Column(length = 1000)
    private String stars;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(length = 20)
    private String rated;

    @Column(name = "poster_path", length = 255)
    private String posterPath;

    private Integer seasons;

    @Column(name = "imdb_url", length = 500)
    private String imdbUrl;
}
