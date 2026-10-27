package com.psych.ai.repository;

import com.psych.ai.entity.AssessmentScale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentScaleRepository extends JpaRepository<AssessmentScale, Long> {
    Optional<AssessmentScale> findByCode(String code);
}
