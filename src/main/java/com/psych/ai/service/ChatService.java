package com.psych.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psych.ai.common.BusinessException;
import com.psych.ai.config.DeepSeekProperties;
import com.psych.ai.entity.ChatMessage;
import com.psych.ai.entity.ChatSession;
import com.psych.ai.repository.ChatMessageRepository;
import com.psych.ai.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final DeepSeekService deepSeek;
    private final SafetyService safety;
    private final DeepSeekProperties props;

    @Transactional
    public ChatSession createSession(Long userId) {
        ChatSession s = new ChatSession();
        s.setUserId(userId);
        s.setTitle("新对话");
        return sessionRepo.save(s);
    }

    public List<ChatSession> listSessions(Long userId) {
        return sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatMessage> messages(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    /** 同步对话（备用通道） */
    @Transactional
    public ChatMessage send(Long userId, Long sessionId, String content) {
        ChatSession session = ownedSession(userId, sessionId);
        content = content == null ? "" : content.trim();
        if (content.isEmpty()) {
            throw new BusinessException("消息不能为空");
        }
        saveMsg(session, "user", content);
        refreshTitle(session, content);

        String reply = safety.isCrisis(content)
                ? safety.crisisResponse()
                : deepSeek.chat(buildMessages(session.getId()));
        return saveMsg(session, "assistant", reply);
    }

    /**
     * 流式对话（优化点）：
     * - 危机信号前置拦截，命中后不请求大模型，直接返回干预话术
     * - 大模型流式输出经 SSE 实时推给前端
     * - 流式建立失败 → 自动降级为同步重试；生成中断 → 保存已生成内容并提示用户
     */
    @Async
    @Transactional
    public void streamReply(Long userId, Long sessionId, String content, SseEmitter emitter) {
        try {
            ChatSession session = ownedSession(userId, sessionId);
            content = content == null ? "" : content.trim();
            if (content.isEmpty()) {
                throw new BusinessException("消息不能为空");
            }

            // 1. 危机干预前置拦截
            if (safety.isCrisis(content)) {
                saveMsg(session, "user", content);
                String crisis = safety.crisisResponse();
                saveMsg(session, "assistant", crisis);
                safeSend(emitter, crisis);
                finish(emitter);
                return;
            }

            saveMsg(session, "user", content);
            refreshTitle(session, content);
            List<Map<String, String>> msgs = buildMessages(session.getId());
            StringBuilder buf = new StringBuilder();

            // 2. 流式调用大模型
            deepSeek.streamChat(msgs)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnNext(token -> {
                        buf.append(token);
                        safeSend(emitter, token);
                    })
                    .doOnComplete(() -> {
                        String reply = buf.toString();
                        if (!reply.isBlank()) {
                            saveMsg(session, "assistant", reply);
                        }
                        session.setUpdatedAt(LocalDateTime.now());
                        sessionRepo.save(session);
                        finish(emitter);
                    })
                    .doOnError(e -> {
                        log.error("流式对话异常: {}", e.getMessage());
                        try {
                            if (buf.length() == 0) {
                                // 连接阶段失败：降级为同步接口（内部含 3 次重试）
                                String reply = deepSeek.chat(msgs);
                                saveMsg(session, "assistant", reply);
                                safeSend(emitter, reply);
                            } else {
                                // 生成中断：保存已生成部分，避免丢失
                                saveMsg(session, "assistant", buf + "\n\n（回答生成中断，可重新提问继续）");
                                safeSend(emitter, "\n\n（回答生成中断，可重新提问继续）");
                            }
                            finish(emitter);
                        } catch (Exception ex) {
                            log.warn("SSE 降级处理失败", ex);
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            safeSend(emitter, "出错了：" + e.getMessage());
            finish(emitter);
        }
    }

    /** 组装大模型消息：System Prompt + 最近 N 条历史（控制 token 成本） */
    private List<Map<String, String>> buildMessages(Long sessionId) {
        List<ChatMessage> history =
                messageRepo.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(history);
        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", safety.systemPrompt()));
        for (ChatMessage m : history) {
            msgs.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        return msgs;
    }

    private void refreshTitle(ChatSession session, String content) {
        if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
            session.setTitle(content.length() > 20 ? content.substring(0, 20) + "…" : content);
            sessionRepo.save(session);
        }
    }

    private ChatMessage saveMsg(ChatSession session, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(session.getId());
        m.setUserId(session.getUserId());
        m.setRole(role);
        m.setContent(content);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepo.save(session);
        return messageRepo.save(m);
    }

    private ChatSession ownedSession(Long userId, Long sessionId) {
        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!s.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        return s;
    }

    private void safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    private void finish(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (IOException | IllegalStateException ignored) {
        }
    }
}
