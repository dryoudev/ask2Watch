package com.ask2watch.repository;

import com.ask2watch.model.PickOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PickOfWeekRepository extends JpaRepository<PickOfWeek, Long> {

    List<PickOfWeek> findByUserIdAndWeekDate(Long userId, LocalDate weekDate);

    List<PickOfWeek> findByUserId(Long userId);
}
