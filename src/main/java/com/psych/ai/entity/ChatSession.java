package com.psych.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 咨询会话 */
@Data
@Entity
@Table(name = "t_chat_session", indexes = @Index(columnList = "userId"))
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 60)
    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}
