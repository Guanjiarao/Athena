

package com.nageoffer.ai.ragent.triage.question;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.response.TriageReplyPromptSupport;
import com.nageoffer.ai.ragent.triage.rule.SlotRuleDefinition;
import com.nageoffer.ai.ragent.triage.rule.TriageSlotRuleService;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cold-start slot selector for RuleAgent miss scenarios.
 *
 * <p>This component owns the LLM-based slot scoring fallback used when Redis/DB rules do not
 * provide a reusable next question. It is intentionally separated from {@link QuestionPlanningSupport}
 * so regular planning and cold-start learning can evolve independently.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColdStartSlotSelector {

    private static final String SLOT_SCORING_SYSTEM_PROMPT = """
你是医疗分诊系统的冷启动槽位规则学习器。

系统架构说明：
- 常见症状的追问规则优先来自 PostgreSQL，并缓存在 Redis。
- 只有当 DB/Redis 没有命中可追问规则，或现有规则都已问完时，才会调用你。
- 你的结果会用于本轮选择下一个槽位，并把高置信槽位学习进 Redis，供后续相同 signal 复用。

你的任务：
1. 在候选槽位中评估哪些信息对当前症状最值得继续追问。
2. 不要重复选择已收集、已回答、最近问过或用户刚刚回答过的槽位。
3. 如果当前信息已经足够，或继续追问收益很低，应建议 generate_report。
4. 如果继续追问，优先选择对风险分层、危险信号排查、关键诊断分流最有价值的槽位。

评分标准（0-100分）：
- 90-100分：当前症状下高度必要，优先学习为规则并立即追问
- 70-89分：重要，适合学习为该 signal 的常用规则
- 50-69分：有一定帮助，但不应优先于高价值槽位
- 30-49分：价值有限，通常不应追问
- 0-29分：无关、重复、已回答或不建议追问

输出格式（严格 JSON）：
{
  "scores": [
    {
      "slot": "槽位代码",
      "score": 分数(0-100),
      "reason": "评分理由（一句话，说明为什么该槽位适合或不适合当前 signal）"
    }
  ],
  "recommendation": "continue" 或 "generate_report",
  "rationale": "整体推荐理由"
}

硬性要求：
- 只能评价候选槽位列表中出现的 slot。
- 对已收集、已回答、最近问过、当前轮刚回答的槽位必须给低分。
- 如果连续兜底次数 ≥ 3 次，强烈倾向 generate_report，避免追问死循环。
- 如果连续兜底次数 ≥ 4 次，除非有明确高危风险需要确认，否则必须 generate_report。
- 不要为了凑轮次而追问。
- 不要输出 JSON 以外的内容。
""";

    private static final int SLOT_SCORE_THRESHOLD = 30;

    private final TriageModelGateway triageModelGateway;
    private final TriageSlotRuleService triageSlotRuleService;

    public QuestionPlan select(TriageContext context, int consecutiveFallbackCount) {
        log.warn("[ColdStartSlotSelector] 启动 LLM 冷启动槽位选择，连续兜底次数: {}", consecutiveFallbackCount);

        List<SlotCode> candidateSlots = collectCandidateSlots(context);
        if (candidateSlots.isEmpty()) {
            log.warn("[ColdStartSlotSelector] 没有可用候选槽位，返回空计划");
            return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .priorityReason("所有冷启动候选槽位都已填充，建议生成报告")
                    .build();
        }

        String userPrompt = buildSlotScoringPrompt(context, candidateSlots, consecutiveFallbackCount);
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(SLOT_SCORING_SYSTEM_PROMPT));
            messages.add(ChatMessage.user(userPrompt));

            String response = triageModelGateway.chatWithReportModel(messages, 0.3D, 0.5D, 800);
            log.info("[ColdStartSlotSelector] LLM 槽位评分响应: {}", response);

            SlotScoringResult scoringResult = parseSlotScoringResponse(response);
            if (scoringResult == null) {
                return fallbackToDefaultSlot(candidateSlots);
            }

            if ("generate_report".equals(scoringResult.getRecommendation())) {
                return QuestionPlan.builder()
                        .nextSlotsToAsk(List.of())
                        .priorityReason("LLM 冷启动评估：" + scoringResult.getRationale())
                        .build();
            }

            SlotScore selectedScore = selectBestValidScore(scoringResult);
            if (selectedScore == null) {
                return QuestionPlan.builder()
                        .nextSlotsToAsk(List.of())
                        .priorityReason("所有候选槽位分数低于阈值或缺少有效问题模板，建议生成报告")
                        .build();
            }

            cacheHighConfidenceSlotScores(context, scoringResult);

            return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of(selectedScore.getSlot()))
                    .priorityReason("LLM 冷启动选择：" + selectedScore.getReason() + "（分数：" + selectedScore.getScore() + "）")
                    .build();
        } catch (Exception e) {
            log.error("[ColdStartSlotSelector] LLM 槽位评分失败", e);
            return fallbackToDefaultSlot(candidateSlots);
        }
    }

    private SlotScore selectBestValidScore(SlotScoringResult scoringResult) {
        if (scoringResult.getScores() == null || scoringResult.getScores().isEmpty()) {
            return null;
        }
        SlotScore topScore = scoringResult.getScores().stream()
                .max(Comparator.comparingInt(SlotScore::getScore))
                .orElse(null);
        if (topScore == null || topScore.getScore() < SLOT_SCORE_THRESHOLD) {
            return null;
        }
        if (hasValidQuestionTemplate(topScore.getSlot())) {
            return topScore;
        }
        return scoringResult.getScores().stream()
                .filter(score -> score.getScore() >= SLOT_SCORE_THRESHOLD)
                .filter(score -> hasValidQuestionTemplate(score.getSlot()))
                .max(Comparator.comparingInt(SlotScore::getScore))
                .orElse(null);
    }

    private List<SlotCode> collectCandidateSlots(TriageContext context) {
        List<SlotCode> allFallbackSlots = List.of(
                SlotCode.DURATION, SlotCode.ONSET_TIME,
                SlotCode.PAIN_SEVERITY, SlotCode.PAIN_CHARACTER, SlotCode.BODY_PART,
                SlotCode.FEVER_PRESENCE, SlotCode.NAUSEA_PRESENCE, SlotCode.VOMITING_PRESENCE,
                SlotCode.DIARRHEA_PRESENCE, SlotCode.COUGH_PRESENCE, SlotCode.DYSPNEA_PRESENCE,
                SlotCode.DIAGNOSIS_HISTORY, SlotCode.MEDICATION_HISTORY,
                SlotCode.ASSOCIATED_SYMPTOMS, SlotCode.AGE
        );
        if (context == null || context.getSlotState() == null) {
            return allFallbackSlots;
        }
        SlotState slotState = context.getSlotState();
        Set<SlotCode> answeredSlots = context.getAnsweredSlots() == null ? Set.of() : new HashSet<>(context.getAnsweredSlots());
        Set<SlotCode> recentlyAskedSlots = context.getLastAskedSlots() == null ? Set.of() : new HashSet<>(context.getLastAskedSlots());
        return allFallbackSlots.stream()
                .filter(slot -> !answeredSlots.contains(slot))
                .filter(slot -> !recentlyAskedSlots.contains(slot))
                .filter(slot -> {
                    SlotValue slotValue = slotState.get(slot);
                    return slotValue == null
                            || slotValue.getStatus() == null
                            || slotValue.getStatus() == SlotStatus.UNKNOWN;
                })
                .collect(Collectors.toList());
    }

    private String buildSlotScoringPrompt(TriageContext context, List<SlotCode> candidateSlots, int consecutiveFallbackCount) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【当前症状描述】\n");
        prompt.append("用户输入：").append(context == null || context.getUserInput() == null ? "无" : context.getUserInput()).append("\n\n");

        prompt.append("【已提取的结构化症状】\n");
        if (context == null || context.getExtractedSymptoms() == null || context.getExtractedSymptoms().isEmpty()) {
            prompt.append("暂无\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                prompt.append("- 症状：").append(symptom.getName() == null ? "未知" : symptom.getName()).append("\n");
                if (symptom.getBodyPart() != null) prompt.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (symptom.getDuration() != null) prompt.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (symptom.getSeverity() != null) prompt.append("  程度：").append(symptom.getSeverity()).append("\n");
            }
        }
        prompt.append("\n");

        prompt.append("【已收集的信息】\n");
        SlotState slotState = context == null ? null : context.getSlotState();
        if (slotState == null || slotState.getSlots() == null || slotState.getSlots().isEmpty()) {
            prompt.append("暂无\n");
        } else {
            for (Map.Entry<SlotCode, SlotValue> entry : slotState.getSlots().entrySet()) {
                SlotValue value = entry.getValue();
                if (value != null && value.getStatus() != null && value.getStatus() != SlotStatus.UNKNOWN) {
                    prompt.append("- ").append(getSlotDescription(entry.getKey()))
                            .append("：").append(value.getValue() == null ? "未知" : value.getValue())
                            .append("（状态：").append(value.getStatus()).append("）\n");
                }
            }
        }
        prompt.append("\n");

        prompt.append("【当前风险评估】\n");
        RiskLevel riskLevel = context == null ? null : context.getRiskAssessment();
        if (riskLevel != null) {
            prompt.append("风险等级：").append(riskLevel.getLevel()).append("\n");
            prompt.append("风险分数：").append(riskLevel.getScore()).append("\n");
            if (riskLevel.getEvidence() != null) {
                prompt.append("依据：").append(riskLevel.getEvidence()).append("\n");
            }
        } else {
            prompt.append("暂未评估\n");
        }
        prompt.append("\n");

        prompt.append("【连续兜底次数】\n");
        prompt.append("这是第 ").append(consecutiveFallbackCount).append(" 次连续触发 LLM 兜底机制。\n");
        if (consecutiveFallbackCount >= 3) {
            prompt.append("警告：连续兜底次数较多，说明当前症状信息可能已经足够，建议考虑生成报告。\n");
        }
        prompt.append("\n");

        prompt.append("【候选问题槽位】\n");
        for (SlotCode slot : candidateSlots) {
            prompt.append("- ").append(slot.name()).append("：").append(getSlotDescription(slot)).append("\n");
        }
        prompt.append("\n");
        prompt.append("请根据以上信息，评估每个候选槽位的重要性（0-100分），并给出是否继续询问的建议。");
        return prompt.toString();
    }

    private SlotScoringResult parseSlotScoringResponse(String response) {
        if (StrUtil.isBlank(response)) {
            return null;
        }
        try {
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            JsonNode root = new ObjectMapper().readTree(jsonStr.trim());
            List<SlotScore> scores = new ArrayList<>();
            JsonNode scoresNode = root.get("scores");
            if (scoresNode != null && scoresNode.isArray()) {
                for (JsonNode scoreNode : scoresNode) {
                    try {
                        scores.add(SlotScore.builder()
                                .slot(SlotCode.valueOf(scoreNode.get("slot").asText()))
                                .score(scoreNode.get("score").asInt())
                                .reason(scoreNode.get("reason").asText())
                                .build());
                    } catch (Exception ex) {
                        log.warn("[ColdStartSlotSelector] 忽略无法识别的槽位评分节点: {}", scoreNode, ex);
                    }
                }
            }
            return SlotScoringResult.builder()
                    .scores(scores)
                    .recommendation(root.path("recommendation").asText())
                    .rationale(root.path("rationale").asText())
                    .build();
        } catch (Exception e) {
            log.error("[ColdStartSlotSelector] 解析 LLM 响应失败", e);
            return null;
        }
    }

    private void cacheHighConfidenceSlotScores(TriageContext context, SlotScoringResult scoringResult) {
        if (triageSlotRuleService == null || scoringResult == null || scoringResult.getScores() == null || scoringResult.getScores().isEmpty()) {
            return;
        }
        String signal = inferPrimarySignalForLearning(context);
        if (StrUtil.isBlank(signal)) {
            return;
        }
        List<SlotRuleDefinition> learnedRules = scoringResult.getScores().stream()
                .filter(score -> score != null && score.getSlot() != null)
                .map(score -> SlotRuleDefinition.builder()
                        .signal(signal)
                        .slot(score.getSlot())
                        .gapType(QuestionGapType.FOLLOW_UP_REQUIRED)
                        .source(QuestionGapSource.PATTERN)
                        .priority(score.getScore())
                        .reason(StrUtil.blankToDefault(score.getReason(), signal + " 场景由 LLM 评分得到的追问槽位。"))
                        .confidence(score.getScore() / 100.0D)
                        .options(buildLearnedRuleOptions(score.getSlot()))
                        .build())
                .toList();
        triageSlotRuleService.saveLearnedRules(signal, learnedRules);
    }

    private String inferPrimarySignalForLearning(TriageContext context) {
        if (context == null) {
            return null;
        }
        if (StrUtil.isNotBlank(context.getFinalPrimaryComplaint())) {
            return context.getFinalPrimaryComplaint().trim();
        }
        String primarySymptom = slotValue(context.getSlotState(), SlotCode.PRIMARY_SYMPTOM);
        if (StrUtil.isNotBlank(primarySymptom)) {
            return primarySymptom.trim();
        }
        String symptom = slotValue(context.getSlotState(), SlotCode.SYMPTOM);
        if (StrUtil.isNotBlank(symptom)) {
            return symptom.trim();
        }
        if (context.getExtractedSymptoms() != null) {
            return context.getExtractedSymptoms().stream()
                    .filter(symptomValue -> symptomValue != null && StrUtil.isNotBlank(symptomValue.getName()))
                    .map(symptomValue -> symptomValue.getName().trim())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String slotValue(SlotState slotState, SlotCode slotCode) {
        if (slotState == null || slotCode == null) {
            return null;
        }
        SlotValue slotValue = slotState.get(slotCode);
        return slotValue == null ? null : slotValue.getValue();
    }

    private QuestionPlan fallbackToDefaultSlot(List<SlotCode> candidateSlots) {
        if (candidateSlots == null || candidateSlots.isEmpty()) {
            return QuestionPlan.builder().nextSlotsToAsk(List.of()).priorityReason("没有可用的候选槽位").build();
        }
        SlotCode firstSlot = candidateSlots.get(0);
        return QuestionPlan.builder()
                .nextSlotsToAsk(List.of(firstSlot))
                .priorityReason("默认冷启动兜底：选择 " + getSlotDescription(firstSlot))
                .build();
    }

    private boolean hasValidQuestionTemplate(SlotCode slotCode) {
        String prompt = TriageReplyPromptSupport.promptForSlot(slotCode);
        return prompt != null && !prompt.equals("能再详细说说这方面的情况吗？");
    }

    private String getSlotDescription(SlotCode slot) {
        return switch (slot) {
            case DURATION -> "持续时间";
            case ONSET_TIME -> "发作时间";
            case PAIN_SEVERITY -> "疼痛程度";
            case PAIN_CHARACTER -> "疼痛性质";
            case BODY_PART -> "身体部位";
            case FEVER_PRESENCE -> "是否发热";
            case NAUSEA_PRESENCE -> "是否恶心";
            case VOMITING_PRESENCE -> "是否呕吐";
            case DIARRHEA_PRESENCE -> "是否腹泻";
            case COUGH_PRESENCE -> "是否咳嗽";
            case DYSPNEA_PRESENCE -> "是否呼吸困难";
            case DIAGNOSIS_HISTORY -> "既往病史";
            case MEDICATION_HISTORY -> "用药史";
            case ASSOCIATED_SYMPTOMS -> "伴随症状";
            case AGE -> "年龄";
            default -> slot.name();
        };
    }

    private List<TriageClarificationData.QuestionOption> buildLearnedRuleOptions(SlotCode slot) {
        if (slot == null) {
            return List.of();
        }
        return switch (slot) {
            case PAIN_SEVERITY -> List.of(option("轻微", "mild", slot), option("中等", "moderate", slot), option("严重", "severe", slot), option("难以忍受", "unbearable", slot), option("其他", "other", slot));
            case PAIN_CHARACTER -> List.of(option("刺痛", "sharp", slot), option("钝痛", "dull", slot), option("胀痛", "distending", slot), option("放射痛/窜痛", "radiating", slot), option("其他", "other", slot));
            case ASSOCIATED_SYMPTOMS -> List.of(option("麻木或无力", "numbness_weakness", slot), option("放射到腿/臀部", "radiating_leg_hip", slot), option("排尿异常", "urinary_abnormality", slot), option("发热或寒战", "fever_chills", slot), option("其他", "other", slot));
            case ONSET_TIME -> List.of(option("突然开始", "sudden", slot), option("逐渐出现", "gradual", slot), option("活动/劳累后出现", "after_activity", slot), option("反复发作", "recurrent", slot), option("其他", "other", slot));
            case FEVER_PRESENCE -> List.of(option("有发热", "yes", slot), option("没有发热", "no", slot), option("发冷/寒战", "chills", slot), option("不确定", "uncertain", slot), option("其他", "other", slot));
            case DYSPNEA_PRESENCE -> List.of(option("有呼吸困难", "yes", slot), option("没有呼吸困难", "no", slot), option("活动后气短", "exertional", slot), option("胸闷伴气短", "chest_tightness", slot), option("其他", "other", slot));
            case DIAGNOSIS_HISTORY -> List.of(option("有相关既往病史", "yes", slot), option("没有相关病史", "no", slot), option("曾经类似发作", "similar_before", slot), option("不确定", "uncertain", slot), option("其他", "other", slot));
            case MEDICATION_HISTORY -> List.of(option("还没用药", "none", slot), option("用过止痛药", "painkiller", slot), option("用药后有缓解", "relieved", slot), option("用药后无缓解", "not_relieved", slot), option("其他", "other", slot));
            case AGE -> List.of(option("18岁以下", "under_18", slot), option("18-40岁", "18_40", slot), option("41-60岁", "41_60", slot), option("60岁以上", "over_60", slot), option("其他", "other", slot));
            case BODY_PART -> List.of(option("左侧", "left", slot), option("右侧", "right", slot), option("双侧", "both", slot), option("中间/正中", "middle", slot), option("其他", "other", slot));
            default -> List.of(option("有", "yes", slot), option("没有", "no", slot), option("轻微", "mild", slot), option("明显", "obvious", slot), option("其他", "other", slot));
        };
    }

    private TriageClarificationData.QuestionOption option(String label, String value, SlotCode targetSlot) {
        return TriageClarificationData.QuestionOption.builder()
                .label(label)
                .value(value)
                .targetSlot(targetSlot)
                .build();
    }

    @Data
    @Builder
    private static class SlotScoringResult {
        private List<SlotScore> scores;
        private String recommendation;
        private String rationale;
    }

    @Data
    @Builder
    private static class SlotScore {
        private SlotCode slot;
        private int score;
        private String reason;
    }
}
