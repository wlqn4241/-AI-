package com.psych.ai.repository;

import com.psych.ai.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
