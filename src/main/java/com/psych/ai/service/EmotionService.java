package com.psych.ai.service;

import com.psych.ai.entity.EmotionRecord;
import com.psych.ai.repository.EmotionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmotionService {

    private final EmotionRecordRepository emotionRepo;

    /** 保存/更新当日情绪记录（每人每天一条） */
    @Transactional
    public EmotionRecord save(Long userId, LocalDate date, Integer mood, List<String> tags, String content) {
        if (mood == null || mood < 1 || mood > 5) {
            mood = 3;
        }
        LocalDate d = date == null ? LocalDate.now() : date;
        EmotionRecord record = emotionRepo.findByUserIdAndDate(userId, d).orElseGet(EmotionRecord::new);
        record.setUserId(userId);
        record.setDate(d);
        record.setMood(mood);
        record.setTags(tags == null ? "" : String.join(",", tags));
        record.setContent(content);
        return emotionRepo.save(record);
    }

    /** 最近 N 天记录（升序，供趋势图使用） */
    public List<EmotionRecord> recent(Long userId, int days) {
        LocalDate end = LocalDate.now();
        return emotionRepo.findByUserIdAndDateBetweenOrderByDateAsc(userId, end.minusDays(days - 1L), end);
    }

    /** 统计：趋势 + 平均分 + 标签频次（优化点：为可视化图表提供结构化数据） */
    public Map<String, Object> stats(Long userId, int days) {
        List<EmotionRecord> records = recent(userId, days);
        List<Map<String, Object>> trend = records.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", r.getDate().toString());
                    m.put("mood", r.getMood());
                    return m;
                })
                .collect(Collectors.toList());

        double avg = records.stream().mapToInt(EmotionRecord::getMood).average().orElse(0);

        Map<String, Long> tagCount = new LinkedHashMap<>();
        records.forEach(r -> {
            if (r.getTags() != null && !r.getTags().isBlank()) {
                Arrays.stream(r.getTags().split(","))
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .forEach(t -> tagCount.merge(t, 1L, Long::sum));
            }
        });
        List<Map<String, Object>> tags = tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", trend);
        result.put("avgMood", Math.round(avg * 100) / 100.0);
        result.put("count", records.size());
        result.put("tags", tags);
        return result;
    }

    /** 历史列表（倒序，供日记列表展示） */
    public List<EmotionRecord> list(Long userId, int limit) {
        return emotionRepo.findByUserIdOrderByDateDesc(userId, PageRequest.of(0, limit));
    }
}
