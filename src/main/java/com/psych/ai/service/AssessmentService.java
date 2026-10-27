package com.psych.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psych.ai.common.BusinessException;
import com.psych.ai.entity.AssessmentRecord;
import com.psych.ai.entity.AssessmentScale;
import com.psych.ai.repository.AssessmentRecordRepository;
import com.psych.ai.repository.AssessmentScaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 心理测评引擎（优化点）：
 * 1. 量表题目/选项分值/评分区间全部数据驱动（JSON），新增量表无需改代码
 * 2. 规则报告（总分/等级/需关注条目/危机条目预警）+ AI 个性化建议 双重生成
 * 3. PHQ-9 第 9 题等危机条目得分 > 0 时在报告中强提示援助热线
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentScaleRepository scaleRepo;
    private final AssessmentRecordRepository recordRepo;
    private final DeepSeekService deepSeek;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AssessmentScale> listScales() {
        return scaleRepo.findAll();
    }

    public AssessmentScale getScale(String code) {
        return scaleRepo.findByCode(code).orElseThrow(() -> new BusinessException("量表不存在"));
    }

    @Transactional
    public AssessmentRecord submit(Long userId, String code, List<Integer> answers) {
        AssessmentScale scale = getScale(code);
        List<Map<String, Object>> questions = parseQuestions(scale.getQuestionsJson());
        if (answers == null || answers.size() != questions.size()) {
            throw new BusinessException("答题数量与题目数量不一致");
        }

        int total = 0;
        List<String> highlights = new ArrayList<>();
        boolean crisisItem = false;

        for (int i = 0; i < questions.size(); i++) {
            int idx = answers.get(i) == null ? -1 : answers.get(i);
            List<Map<String, Object>> options = castOptions(questions.get(i).get("options"));
            if (idx < 0 || idx >= options.size()) {
                throw new BusinessException("第 " + (i + 1) + " 题未作答");
            }
            int score = ((Number) options.get(idx).get("score")).intValue();
            total += score;

            // 高分条目：得分达到该量表选项的最高或次高档
            if (score >= options.size() - 2) {
                highlights.add("第 " + (i + 1) + " 题「" + questions.get(i).get("text") + "」");
            }
            // 危机条目（如 PHQ-9 第 9 题）
            if (Boolean.TRUE.equals(questions.get(i).get("crisis")) && score > 0) {
                crisisItem = true;
            }
        }

        Map<String, Object> band = findBand(scale.getScoringJson(), total);
        String aiSuggestion = aiSuggestion(scale, total, band);
        String report = buildReport(scale, total, band, highlights, crisisItem, aiSuggestion);

        AssessmentRecord record = new AssessmentRecord();
        record.setUserId(userId);
        record.setScaleCode(scale.getCode());
        record.setScaleName(scale.getName());
        record.setAnswersJson(toJson(answers));
        record.setScore(total);
        record.setSeverity(String.valueOf(band.get("label")));
        record.setReport(report);
        return recordRepo.save(record);
    }

    public List<AssessmentRecord> records(Long userId) {
        return recordRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AssessmentRecord recordDetail(Long userId, Long id) {
        AssessmentRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new BusinessException("记录不存在"));
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("记录不存在");
        }
        return r;
    }

    // ---------- 内部方法 ----------

    private Map<String, Object> findBand(String scoringJson, int total) {
        for (Map<String, Object> band : parseQuestions(scoringJson)) {
            int min = ((Number) band.get("min")).intValue();
            int max = ((Number) band.get("max")).intValue();
            if (total >= min && total <= max) {
                return band;
            }
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("label", "未知等级");
        fallback.put("suggestion", "建议咨询专业人士进一步评估。");
        return fallback;
    }

    private String buildReport(AssessmentScale scale, int total, Map<String, Object> band,
                               List<String> highlights, boolean crisisItem, String aiSuggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("【量表】").append(scale.getName()).append("\n");
        sb.append("【得分】").append(total).append(" 分\n");
        sb.append("【等级】").append(band.get("label")).append("\n\n");

        if (!highlights.isEmpty()) {
            sb.append("【需关注的条目】\n");
            highlights.forEach(h -> sb.append("· ").append(h).append("\n"));
            sb.append("\n");
        }

        if (crisisItem) {
            sb.append("【重要提示】你在与自伤/轻生念头相关的条目上有所作答。如果这些感受正在发生，请立即联系信任的人，")
              .append("或拨打 24 小时心理援助热线 12356 / 希望24热线 400-161-9995。你值得被认真对待。\n\n");
        }

        sb.append("【解读与建议】\n").append(band.get("suggestion")).append("\n\n");

        if (aiSuggestion != null && !aiSuggestion.isBlank()) {
            sb.append("【AI 个性化建议】\n").append(aiSuggestion).append("\n\n");
        }

        sb.append("※ 本结果基于自评量表，仅供参考，不构成医学诊断。如持续困扰，请寻求专业心理/医疗帮助。");
        return sb.toString();
    }

    /** AI 个性化建议：失败不影响主流程（静默降级为纯规则报告） */
    private String aiSuggestion(AssessmentScale scale, int total, Map<String, Object> band) {
        try {
            List<Map<String, String>> msgs = List.of(
                    Map.of("role", "system", "content",
                            "你是一位温和专业的心理健康顾问。请根据测评结果给出简短（150 字内）、具体、可行动的建议，"
                                    + "不做诊断，语气温暖有力量，使用简体中文。"),
                    Map.of("role", "user", "content",
                            "用户完成了「" + scale.getName() + "」，总分 " + total + " 分，结果等级为「"
                                    + band.get("label") + "」。请给出针对性的自我调适建议。"));
            return deepSeek.chat(msgs);
        } catch (Exception e) {
            log.warn("AI 建议生成失败，降级为规则报告: {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestions(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            throw new BusinessException("量表数据异常");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castOptions(Object options) {
        return (List<Map<String, Object>>) options;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
