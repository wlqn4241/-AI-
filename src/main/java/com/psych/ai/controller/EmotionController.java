package com.psych.ai.controller;

import com.psych.ai.common.ApiResponse;
import com.psych.ai.dto.Dtos.EmotionReq;
import com.psych.ai.entity.EmotionRecord;
import com.psych.ai.service.EmotionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
public class EmotionController {

    private final EmotionService emotionService;

    private Long userId(HttpServletRequest req) {
        return (Long) req.getAttribute("userId");
    }

    /** 保存/更新情绪记录（每人每天一条） */
    @PostMapping
    public ApiResponse<EmotionRecord> save(@RequestBody EmotionReq body, HttpServletRequest req) {
        LocalDate date = null;
        if (body.date() != null && !body.date().isBlank()) {
            try {
                date = LocalDate.parse(body.date());
            } catch (DateTimeParseException e) {
                date = LocalDate.now();
            }
        }
        return ApiResponse.ok(emotionService.save(userId(req), date, body.mood(), body.tags(), body.content()));
    }

    /** 最近 N 天趋势数据（默认 14 天） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(defaultValue = "14") int days,
                                                  HttpServletRequest req) {
        return ApiResponse.ok(emotionService.stats(userId(req), Math.min(Math.max(days, 7), 90)));
    }

    /** 历史日记列表（最近 100 条） */
    @GetMapping("/list")
    public ApiResponse<List<EmotionRecord>> list(HttpServletRequest req) {
        return ApiResponse.ok(emotionService.list(userId(req), 100));
    }
}
