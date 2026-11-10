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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * DeepSeek AI 接入层 —— 修复版：兼容智谱/通义等 SSE 流式模型
 * 修复点：
 * 1. 去掉 takeUntil("[DONE]") — 智谱等模型不发 [DONE] 结束标记
 * 2. extractStreamContent() 正确拆分 WebClient chunk 内多行 SSE data: {...}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeepSeekService {
    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties props;
    private final ObjectMapper objectMapper;

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
                log.warn("大模型调用失败（第 {} 次）: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.error("大模型重试 3 次仍失败", last);
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    public Flux<String> streamChat(List<Map<String, String>> messages) {
        return Flux.defer(() -> deepSeekWebClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(buildBody(messages, true))
                        .retrieve()
                        .bodyToFlux(String.class))
                .timeout(Duration.ofSeconds(props.getStreamTimeoutSeconds()))
                // 🔧 修复：去掉 takeUntil，让 Flux 自然完成；先拆 SSE 多行再逐行解析
                .concatMap(this::extractStreamContent)
                .filter(s -> s != null && !s.isEmpty())
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof BusinessException))
                        .transientErrors(true));
    }

    /**
     * 解析一个 SSE 分片。
     * 兼容两种形态（WebClient 的 ServerSentEventHttpMessageReader 与字符串解码器行为不同）：
     * 1. 整段保留 "data: {...}" 前缀的原始行；
     * 2. 已经被解包成纯 JSON 载荷（不含 "data:" 前缀）。
     * 因此不能再用 startsWith("data:") 直接过滤，否则会把所有分片丢掉，
     * 导致前端只收到 [DONE]、气泡里只剩兜底文案。
     */
    private Flux<String> extractStreamContent(String chunk) {
        if (chunk == null || chunk.isBlank()) return Flux.empty();
        log.debug("SSE 原始分片: {}", chunk);
        List<String> out = new ArrayList<>();
        for (String rawLine : chunk.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")
                    || line.startsWith("id:") || line.startsWith("retry:")) {
                continue;
            }
            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }
            if (line.isEmpty() || "[DONE]".equals(line)) {
                continue;
            }
            String delta = extractDelta(line);
            if (delta != null && !delta.isEmpty()) {
                out.add(delta);
            } else {
                log.debug("跳过无法解析的 SSE 数据: {}", line);
            }
        }
        return Flux.fromIterable(out);
    }

    private String extractDelta(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                if (!delta.isMissingNode() && delta.has("content")) {
                    return delta.path("content").asText(null);
                }
            }
        } catch (Exception e) { log.debug("解析 SSE chunk 失败: {}", raw); }
        return null;
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

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> resp) {
        if (resp == null) throw new BusinessException("AI 返回内容为空");
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) throw new BusinessException("AI 返回内容为空");
        Map<String, Object> message = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null || content.toString().isBlank()) throw new BusinessException("AI 返回内容为空");
        return content.toString();
    }
}
