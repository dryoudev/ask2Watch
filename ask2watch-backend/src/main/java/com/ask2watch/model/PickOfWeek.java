package com.ask2watch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "picks_of_week")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickOfWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_date", nullable = false)
    private LocalDate weekDate;

    @Column(name = "created_by_agent")
    private Boolean createdByAgent;
}
