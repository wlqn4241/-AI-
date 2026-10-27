package com.psych.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 对话消息（role: user / assistant） */
@Data
@Entity
@Table(name = "t_chat_message", indexes = {@Index(columnList = "sessionId"), @Index(columnList = "userId")})
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
