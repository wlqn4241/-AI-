package com.psych.ai.config;

import com.psych.ai.entity.AssessmentScale;
import com.psych.ai.repository.AssessmentScaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时内置经典心理量表（仅首次初始化）：
 * - PHQ-9 抑郁自评量表
 * - GAD-7 广泛性焦虑量表
 * - PSS-10 知觉压力量表
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AssessmentScaleRepository scaleRepo;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (scaleRepo.count() > 0) {
            return;
        }
        scaleRepo.saveAll(List.of(phq9(), gad7(), pss10()));
        log.info("已内置 3 个心理量表：PHQ-9 / GAD-7 / PSS-10");
    }

    // ---------- 工具方法 ----------

    private List<Map<String, Object>> options(List<String> labels, boolean reverse) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            int score = reverse ? labels.size() - 1 - i : i;
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("label", labels.get(i));
            o.put("score", score);
            list.add(o);
        }
        return list;
    }

    private Map<String, Object> question(String text, List<Map<String, Object>> options, boolean crisis) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("text", text);
        q.put("options", options);
        if (crisis) {
            q.put("crisis", true);
        }
        return q;
    }

    private Map<String, Object> band(int min, int max, String label, String suggestion) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("min", min);
        b.put("max", max);
        b.put("label", label);
        b.put("suggestion", suggestion);
        return b;
    }

    // ---------- PHQ-9 ----------

    private AssessmentScale phq9() {
        List<Map<String, Object>> opt = options(List.of("完全不会", "好几天", "一半以上的天数", "几乎每天"), false);
        List<String> items = List.of(
                "做事时提不起劲或没有兴趣",
                "感到心情低落、沮丧或绝望",
                "入睡困难、睡不安稳或睡眠过多",
                "感觉疲倦或没有活力",
                "食欲不振或吃得太多",
                "觉得自己很糟，或觉得自己很失败，或让自己、家人失望",
                "对事物专注有困难，例如阅读或看电视时难以集中注意力",
                "动作或说话速度缓慢到别人可以察觉，或正好相反——烦躁或坐立不安、动来动去的情况比平常更严重",
                "有不如死掉或用某种方式伤害自己的念头");
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            questions.add(question(items.get(i), opt, i == 8));
        }
        List<Map<String, Object>> bands = List.of(
                band(0, 4, "无抑郁", "目前情绪状态良好，请继续保持规律作息、适度运动和社交，这些都是情绪的天然保护伞。"),
                band(5, 9, "轻度抑郁", "你可能正经历一些情绪低落。建议：保持规律睡眠与运动、每天记录三件让你感觉不错的小事、与信任的人多交流。若两周内无缓解，建议前往学校心理咨询中心或专业机构聊聊。"),
                band(10, 14, "中度抑郁", "你的情绪困扰已比较明显，并对日常生活产生一定影响。强烈建议尽快预约心理咨询师或医院心理科进行面对面评估，专业干预效果通常很好。"),
                band(15, 19, "中重度抑郁", "你正在承受较大的痛苦。请尽快寻求精神科医生或专业心理咨询的帮助——这是勇敢而正确的选择，且中重度抑郁通过规范治疗大多可以显著好转。"),
                band(20, 27, "重度抑郁", "请务必尽快前往医院精神科/心理科就诊，并让家人或朋友知晓你的状况、陪伴你就医。你不需要独自扛着这一切，专业帮助能带来真实的改善。"));

        AssessmentScale s = new AssessmentScale();
        s.setCode("PHQ9");
        s.setName("PHQ-9 抑郁自评量表");
        s.setDescription("国际通用的抑郁症状快速筛查量表，共 9 题，评估最近两周的状态。");
        s.setInstruction("请根据你最近两周内，以下症状出现的频率作答。答案没有对错之分，凭真实感受选择即可。");
        s.setQuestionsJson(toJson(questions));
        s.setScoringJson(toJson(bands));
        return s;
    }

    // ---------- GAD-7 ----------

    private AssessmentScale gad7() {
        List<Map<String, Object>> opt = options(List.of("完全不会", "好几天", "一半以上的天数", "几乎每天"), false);
        List<String> items = List.of(
                "感觉紧张、焦虑或急切",
                "不能停止或控制担忧",
                "对各种各样的事情担忧过多",
                "很难放松下来",
                "由于不安而无法静坐",
                "变得容易烦恼或急躁",
                "感到似乎将有可怕的事情发生而害怕");
        List<Map<String, Object>> questions = new ArrayList<>();
        items.forEach(t -> questions.add(question(t, opt, false)));
        List<Map<String, Object>> bands = List.of(
                band(0, 4, "无焦虑", "目前焦虑水平较低，继续保持你现在的节奏。可以把运动、深呼吸练习当作日常情绪保养。"),
                band(5, 9, "轻度焦虑", "你可能有一些紧绷感。建议尝试：每天 5-10 分钟腹式呼吸或正念练习、减少咖啡因摄入、睡前远离手机。若持续不缓解，可寻求心理咨询支持。"),
                band(10, 14, "中度焦虑", "焦虑已明显影响你的状态。建议尽快进行一次专业心理咨询，学习认知行为疗法（CBT）相关的自助技巧，如焦虑日记、渐进式肌肉放松。"),
                band(15, 21, "重度焦虑", "你的焦虑程度较高，请尽快寻求精神科医生或专业心理咨询师的帮助。规范治疗后重度焦虑同样可以显著缓解，越早干预效果越好。"));

        AssessmentScale s = new AssessmentScale();
        s.setCode("GAD7");
        s.setName("GAD-7 广泛性焦虑量表");
        s.setDescription("国际通用的焦虑症状快速筛查量表，共 7 题，评估最近两周的状态。");
        s.setInstruction("请根据你最近两周内，以下问题困扰你的频率作答。凭真实感受选择即可。");
        s.setQuestionsJson(toJson(questions));
        s.setScoringJson(toJson(bands));
        return s;
    }

    // ---------- PSS-10 ----------

    private AssessmentScale pss10() {
        List<Map<String, Object>> normal = options(List.of("从不", "偶尔", "有时", "时常", "总是"), false);
        List<Map<String, Object>> reverse = options(List.of("从不", "偶尔", "有时", "时常", "总是"), true);
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(question("因为发生了意料之外的事情而感到心烦意乱", normal, false));
        questions.add(question("感觉无法控制自己生活中的重要事情", normal, false));
        questions.add(question("感到紧张不安和有压力", normal, false));
        questions.add(question("自信自己能处理好个人遇到的问题", reverse, false));
        questions.add(question("觉得事情正按自己希望的方向发展", reverse, false));
        questions.add(question("发现自己无法应付所有必须做的事情", normal, false));
        questions.add(question("能控制自己生活中的恼怒情绪", reverse, false));
        questions.add(question("觉得自己能有效应对生活中发生的重大变化", reverse, false));
        questions.add(question("对一些自己无法控制的事情感到恼火", normal, false));
        questions.add(question("觉得困难积累得太多，自己无法克服它们", normal, false));
        List<Map<String, Object>> bands = List.of(
                band(0, 13, "低压力", "你当前的压力知觉处于健康水平，继续保持张弛有度的生活方式。"),
                band(14, 26, "中等压力", "你正承受中等程度的压力。建议识别压力源并拆解任务、保证 7 小时以上睡眠、安排让大脑真正休息的爱好，必要时与朋友或咨询师聊聊。"),
                band(27, 40, "高压力", "你感知到的压力已处于较高水平，长期如此会损害身心。建议尽快梳理压力来源，主动向信任的人或专业咨询师求助，学习时间管理与放松技巧。"));

        AssessmentScale s = new AssessmentScale();
        s.setCode("PSS10");
        s.setName("PSS-10 知觉压力量表");
        s.setDescription("经典压力知觉自评量表，共 10 题（含反向计分题），评估最近一个月的压力水平。");
        s.setInstruction("请根据你最近一个月内的真实感受作答，题目包含正向与反向描述，请逐题认真作答。");
        s.setQuestionsJson(toJson(questions));
        s.setScoringJson(toJson(bands));
        return s;
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("量表初始化失败", e);
        }
    }
}
