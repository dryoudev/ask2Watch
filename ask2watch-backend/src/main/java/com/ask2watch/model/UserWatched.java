package com.ask2watch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "user_watched", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "media_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWatched {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(name = "user_rating")
    private Integer userRating;

    @Column(name = "date_watched")
    private LocalDate dateWatched;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
