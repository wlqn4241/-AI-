package com.psych.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psych.ai.common.BusinessException;
import com.psych.ai.config.DeepSeekProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 接入层（优化点）：
 * 1. 同步接口：超时控制 + 指数退避重试（最多 3 次）
 * 2. 流式接口：SSE 增量返回，连接阶段失败自动重试，避免用户长时间白屏
 * 3. 所有超时/模型参数集中到配置，可调可换
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeepSeekService {

    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties props;
    private final ObjectMapper objectMapper;

    /** 同步对话（非流式），带重试 */
    public String chat(List<Map<String, String>> messages) {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Map<?, ?> resp = deepSeekWebClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(buildBody(messages, false))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(props.getReadTimeoutSeconds()))
                        .block();
                return extractContent(resp);
            } catch (Exception e) {
                last = e;
                log.warn("DeepSeek 调用失败（第 {} 次）: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(1000L * attempt); // 指数退避 1s / 2s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("DeepSeek 重试 3 次仍失败", last);
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    /** 流式对话：返回增量文本片段。连接建立失败会自动重试（最多额外 2 次） */
    public Flux<String> streamChat(List<Map<String, String>> messages) {
        return Flux.defer(() -> deepSeekWebClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(buildBody(messages, true))
                        .retrieve()
                        .bodyToFlux(String.class))
                .timeout(Duration.ofSeconds(props.getStreamTimeoutSeconds()))
                .takeUntil("[DONE]"::equals)
                .mapNotNull(this::extractDelta)
                .filter(StringUtils::hasText)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof BusinessException))
                        .transientErrors(true));
    }

    private Map<String, Object> buildBody(List<Map<String, String>> messages, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);
        return body;
    }

    /** 解析流式 chunk：choices[0].delta.content */
    private String extractDelta(String raw) {
        if (raw == null || "[DONE]".equals(raw.trim())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node.path("choices").path(0).path("delta").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> resp) {
        if (resp == null) {
            throw new BusinessException("AI 返回内容为空");
        }
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException("AI 返回内容为空");
        }
        Map<String, Object> message = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null || content.toString().isBlank()) {
            throw new BusinessException("AI 返回内容为空");
        }
        return content.toString();
    }
}
