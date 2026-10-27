package com.psych.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容安全层（优化点）：
 * 1. 设计 System Prompt 模板，从源头约束大模型回答的角色边界与安全性
 * 2. 前置危机关键词检测：命中自伤/自杀信号时不再请求大模型，直接返回干预话术与援助热线
 */
@Service
public class SafetyService {

    private static final List<String> CRISIS_KEYWORDS = List.of(
            "自杀", "轻生", "想死", "不想活", "活不下去", "结束生命", "结束自己",
            "自残", "自伤", "伤害自己", "割腕", "跳楼", "跳河", "安眠药");

    private static final String CRISIS_RESPONSE = """
            我很担心你现在的状态，也很感谢你愿意告诉我这些。你此刻的感受是真实且重要的，但请相信，再深的痛苦也是可以缓解的，你并不孤单。

            请立即做这几件事：
            1. 现在就联系一位你信任的家人或朋友，让他们陪在你身边；
            2. 拨打专业心理援助热线（24 小时免费）：
               · 全国心理援助热线：12356
               · 北京心理危机研究与干预中心：010-82951332
               · 希望24热线：400-161-9995
            3. 如果情况紧急，请直接拨打 120 或前往就近医院急诊/精神科。

            危机感受会过去，专业的帮助真的有效。你愿意现在就伸出一次手吗？""";

    /** System Prompt 模板：约束角色、边界与回答风格 */
    public String systemPrompt() {
        return """
                你是"暖心"，一位专业、温暖的心理健康 AI 陪伴助手。请严格遵守以下规则：

                1. 你的角色是倾听与陪伴，用共情、温和、不评判的语气交流，先接住情绪，再给建议。
                2. 你不是执业医师或心理治疗师：不做医疗诊断、不开药、不替代专业治疗；在合适时机建议用户寻求学校心理咨询中心或医院精神科/心理科的专业帮助。
                3. 回答保持简洁（一般不超过 300 字），多用开放式提问引导用户表达自己，避免说教式长篇大论。
                4. 不讨论政治、暴力、违法内容；绝不提供任何伤害身体的方法、剂量或工具建议。
                5. 当用户流露自伤、自杀或伤害他人的信号时，立即温和而坚定地劝其远离危险，建议其告知信任的人，并提供心理援助热线：全国心理援助热线 12356。
                6. 保护用户隐私：不询问真实姓名、住址、身份证号等敏感身份信息，不主动追问用户不愿回答的内容。
                7. 若用户的问题明显超出心理健康范畴（如写代码、写作业、闲聊八卦），礼貌说明职责并温和引导回情绪与心理话题。
                8. 全程使用简体中文回复。""";
    }

    /** 危机信号检测 */
    public boolean isCrisis(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CRISIS_KEYWORDS.stream().anyMatch(text::contains);
    }

    public String crisisResponse() {
        return CRISIS_RESPONSE;
    }
}
