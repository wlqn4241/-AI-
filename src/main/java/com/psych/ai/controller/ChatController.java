package com.psych.ai.controller;

import com.psych.ai.common.ApiResponse;
import com.psych.ai.dto.Dtos.ChatSendReq;
import com.psych.ai.entity.ChatMessage;
import com.psych.ai.entity.ChatSession;
import com.psych.ai.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private Long userId(HttpServletRequest req) {
        return (Long) req.getAttribute("userId");
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSession> createSession(HttpServletRequest req) {
        return ApiResponse.ok(chatService.createSession(userId(req)));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> listSessions(HttpServletRequest req) {
        return ApiResponse.ok(chatService.listSessions(userId(req)));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok(chatService.messages(userId(req), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id, HttpServletRequest req) {
        chatService.deleteSession(userId(req), id);
        return ApiResponse.ok();
    }

    /** 同步对话（备用通道，服务端含 3 次重试） */
    @PostMapping("/sessions/{id}/send")
    public ApiResponse<ChatMessage> send(@PathVariable Long id,
                                         @RequestBody ChatSendReq body,
                                         HttpServletRequest req) {
        return ApiResponse.ok(chatService.send(userId(req), id, body.content()));
    }

    /** 流式对话：SSE 增量推送（优化点） */
    @PostMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id,
                             @RequestBody ChatSendReq body,
                             HttpServletRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        chatService.streamReply(userId(req), id, body.content(), emitter);
        return emitter;
    }
}
