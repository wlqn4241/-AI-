package com.psych.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 测评结果记录 */
@Data
@Entity
@Table(name = "t_assessment_record", indexes = @Index(columnList = "userId"))
public class AssessmentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String scaleCode;

    @Column(nullable = false, length = 60)
    private String scaleName;

    /** JSON 数组：每题所选选项下标 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answersJson;

    private Integer score;

    /** 严重程度等级，如 "中度焦虑" */
    @Column(length = 30)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String report;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
