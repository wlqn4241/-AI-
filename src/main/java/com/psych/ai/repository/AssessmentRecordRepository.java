package com.psych.ai.repository;

import com.psych.ai.entity.AssessmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentRecordRepository extends JpaRepository<AssessmentRecord, Long> {
    List<AssessmentRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
