package com.psych.ai.repository;

import com.psych.ai.entity.EmotionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmotionRecordRepository extends JpaRepository<EmotionRecord, Long> {
    Optional<EmotionRecord> findByUserIdAndDate(Long userId, LocalDate date);
    List<EmotionRecord> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate start, LocalDate end);
    List<EmotionRecord> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);
}
