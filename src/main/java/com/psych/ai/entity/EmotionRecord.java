package com.psych.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 情绪记录（每人每天一条，可更新） */
@Data
@Entity
@Table(name = "t_emotion_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "date"}),
        indexes = @Index(columnList = "userId"))
public class EmotionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    /** 心情分 1~5（1 很低落，5 很好） */
    @Column(nullable = false)
    private Integer mood;

    /** 情绪标签，逗号分隔，如 "焦虑,疲惫" */
    @Column(length = 200)
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
