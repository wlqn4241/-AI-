package com.psych.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

/** 心理量表（PHQ-9 / GAD-7 / PSS-10），题目与评分区间以 JSON 存储 */
@Data
@Entity
@Table(name = "t_assessment_scale", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class AssessmentScale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 量表编码，如 PHQ9 */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String instruction;

    /** JSON: [{"text":"...","options":[{"label":"完全不会","score":0}...],"crisis":true}] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionsJson;

    /** JSON: [{"min":0,"max":4,"label":"无抑郁","suggestion":"..."}] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String scoringJson;
}
