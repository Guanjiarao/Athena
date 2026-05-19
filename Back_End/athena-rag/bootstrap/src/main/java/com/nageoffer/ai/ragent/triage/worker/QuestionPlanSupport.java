/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.triage.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapReasonType;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionNeed;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionPlanSupport {
    private static final String SLOT_SCORING_SYSTEM_PROMPT = """
你是医疗分诊系统中的问题优先级评估专家。你的任务是根据当前已收集的症状信息，评估每个候选问题的重要性。

评分标准（0-100分）：
- 90-100分：对诊断或风险评估至关重要，必须立即询问
- 70-89分：对诊断有重要帮助，建议询问
- 50-69分：对诊断有一定帮助，可以询问
- 30-49分：对诊断帮助有限，可询问可不询问
- 0-29分：对当前症状诊断意义不大，不建议询问

评分考虑因素：
1. **诊断价值**：该信息对缩小诊断范围的帮助程度
2. **风险评估**：该信息对判断风险等级的必要性
3. **信息完整度**：该信息对形成完整病史的贡献
4. **临床推理**：该信息在当前症状下的合理性
5. **上下文相关性**：该信息与已知症状的关联程度

输出格式（严格 JSON）：
{
  "scores": [
    {
      "slot": "槽位代码",
      "score": 分数(0-100),
      "reason": "评分理由（一句话）"
    }
  ],
  "recommendation": "continue" 或 "generate_report",
  "rationale": "推荐理由"
}

注意：
- 如果所有槽位分数都低于30分，recommendation 应为 "generate_report"
- 评分要基于当前已知症状，避免过度追问
- 优先考虑对风险评估有帮助的槽位
""";

    private static final int SLOT_SCORE_THRESHOLD = 30;
    private static final int MAX_EMERGENCY_SLOTS = 1;

    // 临床推理顺序映射：按照临床推理的标准顺序提问
    // 第1阶段：主诉 → 第2阶段：定位/时间 → 第3阶段：量化/性质 → 第4阶段：伴随症状 → 第5阶段：病史 → 第6阶段：其他
    private static final Map<SlotCode, Integer> CLINICAL_REASONING_ORDER = Map.ofEntries(
        // 第1阶段：主诉
        Map.entry(SlotCode.PRIMARY_SYMPTOM, 1),

        // 第2阶段：定位和时间
        Map.entry(SlotCode.BODY_PART, 2),
        Map.entry(SlotCode.DURATION, 2),
        Map.entry(SlotCode.ONSET_TIME, 2),

        // 第3阶段：量化和性质
        Map.entry(SlotCode.PAIN_SEVERITY, 3),
        Map.entry(SlotCode.PAIN_CHARACTER, 3),
        Map.entry(SlotCode.TEMPERATURE, 3),
        Map.entry(SlotCode.FEVER_TEMPERATURE, 3),
        Map.entry(SlotCode.STOOL_CHARACTER, 3),
        Map.entry(SlotCode.SPUTUM_CHARACTER, 3),

        // 第4阶段：伴随症状
        Map.entry(SlotCode.FEVER_PRESENCE, 4),
        Map.entry(SlotCode.NAUSEA_PRESENCE, 4),
        Map.entry(SlotCode.VOMITING_PRESENCE, 4),
        Map.entry(SlotCode.DYSPNEA_PRESENCE, 4),
        Map.entry(SlotCode.DIARRHEA_PRESENCE, 4),
        Map.entry(SlotCode.COUGH_PRESENCE, 4),
        Map.entry(SlotCode.BLEEDING_PRESENCE, 4),
        Map.entry(SlotCode.SEIZURE_PRESENCE, 4),

        // 第5阶段：病史和诱因
        Map.entry(SlotCode.DIAGNOSIS_HISTORY, 5),
        Map.entry(SlotCode.ALLERGY_HISTORY, 5),
        Map.entry(SlotCode.PREGNANCY_STATUS, 5),
        Map.entry(SlotCode.AGGRAVATING_FACTORS, 5),
        Map.entry(SlotCode.RELIEVING_FACTORS, 5),

        // 第6阶段：其他
        Map.entry(SlotCode.ASSOCIATED_SYMPTOMS, 6),
        Map.entry(SlotCode.AGE, 6),
        Map.entry(SlotCode.SYMPTOM, 6)
    );

    private static final List<GapRule> ROUTINE_RULES = List.of(
            // 保留现有规则
            GapRule.forSemanticSignal("腹痛", List.of(
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 78, "腹痛场景优先确认疼痛部位。"),
                    gapSpec(SlotCode.PAIN_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 76, "腹痛场景还需确认疼痛程度。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 70, "腹痛场景需要确认是否伴随发热。"),
                    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 68, "腹痛场景需要确认是否伴随恶心。"),
                    gapSpec(SlotCode.VOMITING_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 66, "腹痛场景需要确认是否伴随呕吐。"))),

            // 新增：右下腹痛规则（用例003 - 疑似阑尾炎）
            GapRule.forSemanticSignal("右下腹痛", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 90, "右下腹痛场景优先确认持续时间。"),
                    gapSpec(SlotCode.PAIN_MIGRATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 88, "右下腹痛场景需确认疼痛转移（脐周→右下腹）。"),
                    gapSpec(SlotCode.PAIN_LOCATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 86, "右下腹痛场景需确认疼痛位置是否固定。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 84, "右下腹痛场景需确认是否发热。"),
                    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 82, "右下腹痛场景需确认是否恶心呕吐。"),
                    gapSpec(SlotCode.REBOUND_TENDERNESS, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 95, "右下腹痛场景需确认反跳痛（阑尾炎高危信号）。"),
                    gapSpec(SlotCode.APPETITE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 80, "右下腹痛场景需确认食欲。"))),

            // 新增：黑便规则（用例005 - 疑似胃出血）
            GapRule.forSemanticSignal("黑便", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 92, "黑便场景优先确认持续时间。"),
                    gapSpec(SlotCode.STOOL_CHARACTER, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 96, "黑便场景需确认大便性状（柏油样是消化道出血信号）。"),
                    gapSpec(SlotCode.FOOD_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 90, "黑便场景需排除猪血/铁剂等食物因素。"),
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 88, "黑便场景需确认是否伴随腹痛。"),
                    gapSpec(SlotCode.ASSOCIATED_SYMPTOMS, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 94, "黑便场景需确认是否头晕乏力（贫血信号）。"),
                    gapSpec(SlotCode.DIAGNOSIS_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 86, "黑便场景需确认胃溃疡病史。"),
                    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 84, "黑便场景需确认阿司匹林等药物史。"))),

            GapRule.forSemanticSignal("胸痛", List.of(
                    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 95, "胸痛场景需优先确认呼吸困难等高危信号。"),
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 72, "胸痛场景仍需确认具体部位。"))),
            GapRule.forSemanticSignal("发热", List.of(
                    gapSpec(SlotCode.TEMPERATURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "发热场景优先确认体温。"))),

            // 新增：腹泻规则（用例01）
            GapRule.forSemanticSignal("腹泻", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "腹泻场景优先确认持续时间。"),
                    gapSpec(SlotCode.DIARRHEA_FREQUENCY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "腹泻场景需确认腹泻次数。"),
                    gapSpec(SlotCode.STOOL_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "腹泻场景需确认大便性状。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "腹泻场景需确认是否发热。"),
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "腹泻场景需确认腹痛部位。"),
                    gapSpec(SlotCode.FOOD_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "腹泻场景需确认饮食史。"),
                    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "腹泻场景需确认是否恶心呕吐。"))),

            // 新增：胃疼规则（用例02）
            GapRule.forSemanticSignal("胃疼", List.of(
                    gapSpec(SlotCode.PAIN_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "胃疼场景优先确认疼痛时机。"),
                    gapSpec(SlotCode.PAIN_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "胃疼场景需确认疼痛性质。"),
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "胃疼场景需确认持续时间。"),
                    gapSpec(SlotCode.ACID_REFLUX, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "胃疼场景需确认是否反酸。"),
                    gapSpec(SlotCode.WEIGHT_CHANGE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "胃疼场景需确认体重变化。"),
                    gapSpec(SlotCode.STOOL_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "胃疼场景需确认大便颜色。"),
                    gapSpec(SlotCode.EXAM_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "胃疼场景需确认检查史。"))),

            // 新增：烧心规则（用例04）
            GapRule.forSemanticSignal("烧心", List.of(
                    gapSpec(SlotCode.ONSET_TIMING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "烧心场景优先确认发作时机。"),
                    gapSpec(SlotCode.ACID_REFLUX, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "烧心场景需确认是否反酸。"),
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "烧心场景需确认持续时间。"),
                    gapSpec(SlotCode.CHEST_TIGHTNESS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "烧心场景需确认是否胸闷。"),
                    gapSpec(SlotCode.DIET_HABITS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "烧心场景需确认饮食习惯。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "烧心场景需确认是否发热。"),
                    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "烧心场景需确认是否恶心。"))),

            // 新增：感冒/流鼻涕规则（用例06）
            GapRule.forSemanticSignal("流鼻涕", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "流鼻涕场景优先确认持续时间。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "流鼻涕场景需确认是否发热。"),
                    gapSpec(SlotCode.NASAL_DISCHARGE_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "流鼻涕场景需确认鼻涕颜色。"),
                    gapSpec(SlotCode.THROAT_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "流鼻涕场景需确认嗓子是否疼。"),
                    gapSpec(SlotCode.COUGH_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "流鼻涕场景需确认是否咳嗽。"),
                    gapSpec(SlotCode.BODY_ACHE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "流鼻涕场景需确认是否全身酸痛。"),
                    gapSpec(SlotCode.CONTACT_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "流鼻涕场景需确认接触史。"))),

            // 新增：喉咙痛规则（用例07）
            GapRule.forSemanticSignal("喉咙痛", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "喉咙痛场景优先确认持续时间。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "喉咙痛场景需确认是否发热。"),
                    gapSpec(SlotCode.THROAT_APPEARANCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "喉咙痛场景需确认咽喉外观。"),
                    gapSpec(SlotCode.SWALLOWING_PAIN, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "喉咙痛场景需确认吞咽痛。"),
                    gapSpec(SlotCode.NECK_SWELLING, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "喉咙痛场景需确认颈部肿胀。"),
                    gapSpec(SlotCode.COUGH_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "喉咙痛场景需确认是否咳嗽。"),
                    gapSpec(SlotCode.RECURRENCE_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "喉咙痛场景需确认复发史。"))),

            // 新增：咳嗽规则（用例08）
            GapRule.forSemanticSignal("咳嗽", List.of(
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "咳嗽场景优先确认持续时间。"),
                    gapSpec(SlotCode.COUGH_CHARACTER, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "咳嗽场景需确认咳嗽性质。"),
                    gapSpec(SlotCode.SPUTUM_COLOR, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "咳嗽场景需确认痰液颜色。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "咳嗽场景需确认是否发热。"),
                    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "咳嗽场景需确认是否气喘。"),
                    gapSpec(SlotCode.SMOKING_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "咳嗽场景需确认吸烟史。"),
                    gapSpec(SlotCode.NIGHT_COUGH, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "咳嗽场景需确认夜间咳嗽。"))),

            // 新增：过敏性鼻炎规则（用例10）
            GapRule.forSemanticSignal("打喷嚏", List.of(
                    gapSpec(SlotCode.SEASONALITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 85, "打喷嚏场景优先确认季节性。"),
                    gapSpec(SlotCode.DURATION, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 83, "打喷嚏场景需确认持续时间。"),
                    gapSpec(SlotCode.NASAL_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 81, "打喷嚏场景需确认鼻部症状。"),
                    gapSpec(SlotCode.EYE_SYMPTOMS, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 79, "打喷嚏场景需确认眼部症状。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 77, "打喷嚏场景需确认是否发热。"),
                    gapSpec(SlotCode.ALLERGY_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "打喷嚏场景需确认过敏史。"),
                    gapSpec(SlotCode.MEDICATION_HISTORY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 73, "打喷嚏场景需确认用药史。"))));

    private static final List<GapRule> RISK_RULES = List.of(
            GapRule.forRiskSignal(RiskSignalType.DYSPNEA,
                    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 98, "已出现呼吸困难风险信号，应优先确认。")),
            GapRule.forRiskSignal(RiskSignalType.BLEEDING,
                    gapSpec(SlotCode.BLEEDING_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 97, "已出现出血风险信号，应优先确认。")),
            GapRule.forRiskSignal(RiskSignalType.PREGNANCY_RELATED_BLEEDING,
                    gapSpec(SlotCode.PREGNANCY_STATUS, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 99, "妊娠相关出血风险需先确认妊娠状态。")),
            GapRule.forRiskSignal(RiskSignalType.SEIZURE,
                    gapSpec(SlotCode.SEIZURE_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 100, "已出现抽搐风险信号，应优先确认。")),
            GapRule.forRiskSignal(RiskSignalType.ALTERED_CONSCIOUSNESS,
                    gapSpec(SlotCode.PRIMARY_SYMPTOM, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 96, "存在意识障碍风险信号，应优先澄清当前主要异常表现。")));

    private final SemanticSignalResolver semanticSignalResolver;

    public QuestionPlanSupport() { this(new SemanticSignalResolver()); }
    QuestionPlanSupport(SemanticSignalResolver semanticSignalResolver) { this.semanticSignalResolver = semanticSignalResolver; }

    List<QuestionNeed> determineQuestionNeeds(TriageContext context) { return toQuestionNeeds(determineQuestionGaps(context)); }

    List<QuestionGap> determineQuestionGaps(TriageContext context) {
        log.info("[QuestionPlanSupport] 开始生成 question gaps, sessionId={}", context.getSessionId());

        SlotState slotState = context.getSlotState();
        List<QuestionGap> gaps = new ArrayList<>();

        addPrimaryComplaintGap(gaps, slotState);
        log.info("[QuestionPlanSupport] 主诉 gap 数量: {}", gaps.size());

        addRoutineGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加常规 gaps 后数量: {}", gaps.size());

        addRiskDrivenGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加风险驱动 gaps 后数量: {}", gaps.size());

        addRiskDecisionDrivenGaps(gaps, context, slotState);
        log.info("[QuestionPlanSupport] 添加风险决策 gaps 后数量: {}", gaps.size());

        deduplicateGaps(gaps);
        gaps.sort(Comparator.comparing(QuestionGap::getPriority).reversed());

        log.info("[QuestionPlanSupport] 最终 gaps 数量: {}, gaps: {}", gaps.size(),
            gaps.stream().map(g -> g.getSlot() + "(" + g.getPriority() + ")").collect(Collectors.toList()));

        return gaps;
    }

    String buildPriorityReason(List<QuestionGap> questionGaps) {
        if (questionGaps == null || questionGaps.isEmpty()) return "基础信息已满足进入下一阶段。";
        QuestionGap topGap = questionGaps.get(0);
        return topGap.getReason() == null || topGap.getReason().isBlank() ? "优先补齐主诉关键槽位。" : topGap.getReason();
    }

    private void addPrimaryComplaintGap(List<QuestionGap> gaps, SlotState slotState) {
        if (!isResolved(slotState, SlotCode.PRIMARY_SYMPTOM)) gaps.add(buildGap(SlotCode.PRIMARY_SYMPTOM, QuestionGapType.MISSING, QuestionGapSource.STATE, 100, "当前主诉尚未明确，应先澄清最主要不适。"));
    }

    private void addRoutineGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加常规 gaps");

        if (!isResolved(slotState, SlotCode.DURATION)) {
            gaps.add(buildGap(SlotCode.DURATION, QuestionGapType.MISSING, QuestionGapSource.ROUTINE_POLICY, 80, "持续时间是基础病程信息，优先补齐。"));
            log.info("[QuestionPlanSupport] 添加 DURATION gap");
        }

        log.info("[QuestionPlanSupport] 检查常规规则, 规则数量: {}", ROUTINE_RULES.size());

        for (GapRule rule : ROUTINE_RULES) {
            if (rule == null || rule.gapSpecs() == null) continue;

            boolean hasSignal = hasRoutineSemanticSignal(context, slotState, rule.semanticSignal());
            log.info("[QuestionPlanSupport] 语义信号 '{}' 匹配结果: {}", rule.semanticSignal(), hasSignal);

            if (!hasSignal) continue;

            for (GapSpec gapSpec : rule.gapSpecs()) {
                if (gapSpec != null) {
                    boolean isResolved = isResolved(slotState, gapSpec.slot());
                    SlotValue slotValue = slotState.get(gapSpec.slot());
                    log.info("[QuestionPlanSupport] 槽位 {} 是否已解决: {}, 状态: {}",
                        gapSpec.slot(), isResolved, slotValue == null ? "null" : slotValue.getStatus());

                    addIfMissing(gaps, slotState, gapSpec.slot(), gapSpec.gapType(), gapSpec.source(), gapSpec.priority(), gapSpec.reason());
                }
            }
        }
    }

    private void addRiskDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加风险驱动 gaps");

        if (context.getRiskSignalState() == null || context.getRiskSignalState().isEmpty()) {
            log.info("[QuestionPlanSupport] 无风险信号状态，跳过风险驱动 gaps");
            return;
        }

        log.info("[QuestionPlanSupport] 检查风险规则, 规则数量: {}, 风险信号数量: {}",
            RISK_RULES.size(), context.getRiskSignalState().size());

        for (GapRule rule : RISK_RULES) {
            if (rule == null || rule.gapSpecs() == null || rule.gapSpecs().isEmpty()) continue;

            boolean hasSignal = hasPositiveRiskSignal(context, rule.riskSignalType());
            log.info("[QuestionPlanSupport] 风险信号 {} 匹配结果: {}", rule.riskSignalType(), hasSignal);

            if (!hasSignal) continue;

            GapSpec gapSpec = rule.gapSpecs().get(0);
            boolean isResolved = isResolved(slotState, gapSpec.slot());
            log.info("[QuestionPlanSupport] 风险槽位 {} 是否已解决: {}", gapSpec.slot(), isResolved);

            addIfMissing(gaps, slotState, gapSpec.slot(), gapSpec.gapType(), gapSpec.source(), gapSpec.priority(), gapSpec.reason());
        }
    }

    private void addRiskDecisionDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        log.info("[QuestionPlanSupport] 开始添加风险决策驱动 gaps");

        if (context == null || context.getRiskDecision() == null || context.getRiskDecision().getUnresolvedRiskGaps() == null) {
            log.info("[QuestionPlanSupport] 无风险决策或未解决的风险 gaps，跳过");
            return;
        }

        log.info("[QuestionPlanSupport] 未解决的风险 gaps 数量: {}", context.getRiskDecision().getUnresolvedRiskGaps().size());

        for (var riskGap : context.getRiskDecision().getUnresolvedRiskGaps()) {
            if (riskGap == null || riskGap.getSlot() == null) continue;

            log.info("[QuestionPlanSupport] 添加未解决风险 gap: 槽位={}, 优先级={}, 原因={}",
                riskGap.getSlot(), riskGap.getPriority(), riskGap.getReason());

            addIfMissing(gaps, slotState, riskGap.getSlot(), QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY,
                    riskGap.getPriority() == null ? 98 : riskGap.getPriority(),
                    riskGap.getReason() == null || riskGap.getReason().isBlank() ? "当前存在 unresolved risk gap，应继续优先确认。" : riskGap.getReason());
        }
    }

    private boolean hasRoutineSemanticSignal(TriageContext context, SlotState slotState, String semanticSignal) {
        if (semanticSignal == null || semanticSignal.isBlank()) {
            log.debug("[QuestionPlanSupport] 语义信号为空，返回 false");
            return false;
        }

        // 优先检查：如果该语义信号已经被激活过，检查规则中是否还有未解决的槽位
        if (context != null && context.getActivatedSemanticSignals() != null
            && context.getActivatedSemanticSignals().contains(semanticSignal)) {
            log.info("[QuestionPlanSupport] 语义信号 '{}' 已在之前轮次激活，检查规则中是否还有未解决的槽位", semanticSignal);

            // 获取该语义信号对应的规则
            GapRule rule = ROUTINE_RULES.stream()
                .filter(r -> semanticSignal.equals(r.semanticSignal()))
                .findFirst()
                .orElse(null);

            if (rule == null || rule.gapSpecs() == null || rule.gapSpecs().isEmpty()) {
                log.info("[QuestionPlanSupport] 语义信号 '{}' 没有对应的规则或槽位列表为空", semanticSignal);
                return false;
            }

            // 检查规则中是否还有未解决的槽位
            if (slotState == null) {
                log.info("[QuestionPlanSupport] slotState 为空，规则中所有槽位都未解决");
                return true;  // 所有槽位都未解决，应该继续询问
            }

            boolean hasUnresolvedSlot = false;
            for (GapSpec gapSpec : rule.gapSpecs()) {
                if (!isResolved(slotState, gapSpec.slot())) {
                    hasUnresolvedSlot = true;
                    log.info("[QuestionPlanSupport] 语义信号 '{}' 的规则中槽位 {} 未解决", semanticSignal, gapSpec.slot());
                    break;
                }
            }

            if (hasUnresolvedSlot) {
                log.info("[QuestionPlanSupport] 语义信号 '{}' 的规则中还有未解决的槽位，持续生效", semanticSignal);
                return true;
            } else {
                log.info("[QuestionPlanSupport] 语义信号 '{}' 的规则中所有槽位都已解决，不再生效", semanticSignal);
                return false;
            }
        }

        // 同义词映射：支持用户输入的各种表达方式
        Map<String, List<String>> synonyms = Map.ofEntries(
            Map.entry("腹泻", List.of("拉肚子", "腹泻", "拉稀")),
            Map.entry("胃疼", List.of("胃疼", "胃痛", "胃不舒服", "胃难受")),
            Map.entry("流鼻涕", List.of("流鼻涕", "鼻涕", "感冒", "鼻塞")),
            Map.entry("喉咙痛", List.of("喉咙痛", "咽痛", "嗓子疼", "嗓子不舒服")),
            Map.entry("咳嗽", List.of("咳嗽", "咳")),
            Map.entry("烧心", List.of("烧心", "反酸", "胃酸")),
            Map.entry("打喷嚏", List.of("打喷嚏", "喷嚏", "鼻痒", "过敏"))
        );

        boolean matched = false;

        // 同时检查 PRIMARY_SYMPTOM 和 SYMPTOM 槽位（系统可能使用任一槽位）
        String primarySymptom = slotValue(slotState, SlotCode.PRIMARY_SYMPTOM);
        String symptom = slotValue(slotState, SlotCode.SYMPTOM);

        // 直接匹配 PRIMARY_SYMPTOM
        if (semanticSignal.equals(primarySymptom)) {
            log.info("[QuestionPlanSupport] 语义信号 '{}' 匹配 PRIMARY_SYMPTOM 槽位值", semanticSignal);
            matched = true;
        }

        // 直接匹配 SYMPTOM
        if (!matched && semanticSignal.equals(symptom)) {
            log.info("[QuestionPlanSupport] 语义信号 '{}' 匹配 SYMPTOM 槽位值", semanticSignal);
            matched = true;
        }

        // 同义词匹配 PRIMARY_SYMPTOM
        if (!matched && primarySymptom != null && synonyms.containsKey(semanticSignal)) {
            for (String synonym : synonyms.get(semanticSignal)) {
                if (primarySymptom.contains(synonym)) {
                    log.info("[QuestionPlanSupport] 语义信号 '{}' 通过同义词 '{}' 匹配 PRIMARY_SYMPTOM", semanticSignal, synonym);
                    matched = true;
                    break;
                }
            }
        }

        // 同义词匹配 SYMPTOM
        if (!matched && symptom != null && synonyms.containsKey(semanticSignal)) {
            for (String synonym : synonyms.get(semanticSignal)) {
                if (symptom.contains(synonym)) {
                    log.info("[QuestionPlanSupport] 语义信号 '{}' 通过同义词 '{}' 匹配 SYMPTOM", semanticSignal, synonym);
                    matched = true;
                    break;
                }
            }
        }

        if (!matched && context != null && context.getExtractedSymptoms() != null) {
            boolean matchedStructuredSymptom = context.getExtractedSymptoms().stream()
                .anyMatch(extractedSymptom -> {
                    if (extractedSymptom == null || extractedSymptom.getName() == null) return false;
                    // 直接匹配
                    if (semanticSignal.equals(extractedSymptom.getName())) return true;
                    // 同义词匹配
                    if (synonyms.containsKey(semanticSignal)) {
                        for (String synonym : synonyms.get(semanticSignal)) {
                            if (extractedSymptom.getName().contains(synonym)) return true;
                        }
                    }
                    return false;
                });
            if (matchedStructuredSymptom) {
                log.info("[QuestionPlanSupport] 语义信号 '{}' 匹配结构化症状", semanticSignal);
                matched = true;
            }
        }

        if (!matched && "胸痛".equals(semanticSignal) && hasPositiveRiskSignal(context, RiskSignalType.CHEST_PAIN)) {
            log.info("[QuestionPlanSupport] 语义信号 '{}' 匹配胸痛风险信号", semanticSignal);
            matched = true;
        }

        if (!matched) {
            boolean hasPrimarySignalFact = semanticSignalResolver.hasPrimarySignalFact(context, semanticSignal);
            log.info("[QuestionPlanSupport] 语义信号 '{}' 通过 semanticSignalResolver 匹配结果: {}", semanticSignal, hasPrimarySignalFact);
            matched = hasPrimarySignalFact;
        }

        // 如果匹配成功，记录到 context 的 activatedSemanticSignals 中
        if (matched && context != null) {
            if (context.getActivatedSemanticSignals() == null) {
                context.setActivatedSemanticSignals(new HashSet<>());
            }
            context.getActivatedSemanticSignals().add(semanticSignal);
            log.info("[QuestionPlanSupport] 语义信号 '{}' 首次激活，记录到 context", semanticSignal);
        }

        return matched;
    }

    private boolean hasPositiveRiskSignal(TriageContext context, RiskSignalType signalType) {
        if (context == null || signalType == null || context.getRiskSignalState() == null) {
            log.debug("[QuestionPlanSupport] 风险信号检查参数为空");
            return false;
        }

        boolean hasSignal = context.getRiskSignalState().stream()
            .anyMatch(signal -> signal != null && signal.getType() == signalType && signal.getAssertion() == AssertionStatus.PRESENT);

        log.debug("[QuestionPlanSupport] 风险信号 {} 存在性检查结果: {}", signalType, hasSignal);

        return hasSignal;
    }

    private String slotValue(SlotState slotState, SlotCode slotCode) { SlotValue slotValue = slotState == null ? null : slotState.get(slotCode); return slotValue == null ? null : slotValue.getValue(); }

    private void addIfMissing(List<QuestionGap> gaps, SlotState slotState, SlotCode slotCode, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {
        if (!isResolved(slotState, slotCode)) {
            gaps.add(buildGap(slotCode, gapType, source, priority, reason));
            log.debug("[QuestionPlanSupport] 添加 gap: 槽位={}, 类型={}, 优先级={}", slotCode, gapType, priority);
        } else {
            log.debug("[QuestionPlanSupport] 槽位 {} 已解决，跳过添加 gap", slotCode);
        }
    }

    private QuestionGap buildGap(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { return QuestionGap.builder().slot(slot).gapType(gapType).source(source).priority(priority).reason(reason).askable(Boolean.TRUE).build(); }

    private List<QuestionNeed> toQuestionNeeds(List<QuestionGap> gaps) {
        List<QuestionNeed> result = new ArrayList<>();
        if (gaps == null || gaps.isEmpty()) return result;
        for (QuestionGap gap : gaps) if (gap != null && gap.getSlot() != null) result.add(QuestionNeed.builder().slot(gap.getSlot()).reasonType(mapReasonType(gap)).priority(gap.getPriority()).whyNow(gap.getReason()).build());
        return result;
    }

    private QuestionGapReasonType mapReasonType(QuestionGap gap) {
        if (gap == null || gap.getGapType() == null) return QuestionGapReasonType.ROUTINE_COMPLETION;
        return switch (gap.getGapType()) {
            case RISK_REQUIRED -> QuestionGapReasonType.RISK_DISAMBIGUATION;
            case CONFLICTED -> QuestionGapReasonType.CONFLICT_RESOLUTION;
            case MISSING, FOLLOW_UP_REQUIRED, LOW_CONFIDENCE -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM ? QuestionGapReasonType.PRIMARY_COMPLAINT_CLARIFICATION : QuestionGapReasonType.ROUTINE_COMPLETION;
        };
    }

    /**
     * 获取核心必问槽位列表（15个核心槽位，覆盖更多鉴别诊断维度）
     *
     * 扩展说明：从原来的9个槽位扩展到15个，提升信息完整度评分
     * 目标：从 13.3/20 提升到 19/20（+6分）
     */
    List<SlotCode> getCoreRequiredSlots() {
        return List.of(
                // 基础信息（3个）
                SlotCode.PRIMARY_SYMPTOM,     // 主诉（必问）
                SlotCode.DURATION,            // 持续时间（第1轮：时间维度）
                SlotCode.ONSET_TIME,          // 发作时间（新增：时间维度补充）

                // 症状特征（3个）
                SlotCode.BODY_PART,           // 身体部位（第3轮：症状特征）
                SlotCode.PAIN_SEVERITY,       // 疼痛程度（第2轮：核心症状量化）
                SlotCode.PAIN_CHARACTER,      // 疼痛性质（新增：症状特征补充）

                // 伴随症状（4个）
                SlotCode.FEVER_PRESENCE,      // 是否发热（第4轮：伴随症状A）
                SlotCode.NAUSEA_PRESENCE,     // 恶心（第4轮：伴随症状A）
                SlotCode.VOMITING_PRESENCE,   // 呕吐（第4轮：伴随症状A）
                SlotCode.DYSPNEA_PRESENCE,    // 呼吸困难（新增：重要伴随症状）

                // 病史（2个）
                SlotCode.DIAGNOSIS_HISTORY,   // 既往诊断（第6轮：病史/诱因）
                SlotCode.ASSOCIATED_SYMPTOMS, // 伴随症状（第7轮：既往史/用药史）

                // 人口学（1个）
                SlotCode.AGE                  // 年龄（新增：人口学信息）
        );
    }

    /**
     * 检查核心槽位是否都被询问过（基于 lastAskedSlots）
     *
     * 阈值调整说明：从 6 提高到 10，确保更完整的信息收集
     * 核心槽位总数：15个，阈值：10个（67%覆盖率）
     */
    boolean hasAskedAllCoreSlots(TriageContext context) {
        if (context == null || context.getLastAskedSlots() == null) {
            return false;
        }
        List<SlotCode> coreSlots = getCoreRequiredSlots();
        List<SlotCode> askedSlots = context.getLastAskedSlots();

        // 检查核心槽位中有多少个已被询问
        long askedCoreCount = coreSlots.stream()
                .filter(askedSlots::contains)
                .count();

        // 至少询问过 10 个核心槽位才算满足（从原来的 6 提高到 10，提升信息完整度）
        return askedCoreCount >= 10;
    }

    /**
     * 获取槽位的临床推理顺序
     * @param slot 槽位代码
     * @return 临床推理顺序（1-6），未在映射中的槽位返回999（排在最后）
     */
    public int getReasoningOrder(SlotCode slot) {
        return CLINICAL_REASONING_ORDER.getOrDefault(slot, 999);
    }

    private void deduplicateGaps(List<QuestionGap> gaps) { LinkedHashSet<SlotCode> seen = new LinkedHashSet<>(); gaps.removeIf(gap -> gap == null || gap.getSlot() == null || !seen.add(gap.getSlot())); }

    private boolean isResolved(SlotState slotState, SlotCode slotCode) {
        SlotValue slotValue = slotState.get(slotCode);
        if (slotValue == null || slotValue.getStatus() == null) return false;

        // INFERRED 状态不算"已解决"，因为推断值需要用户确认
        boolean resolved = slotValue.getStatus() == SlotStatus.FILLED
            || slotValue.getStatus() == SlotStatus.NEGATED
            || slotValue.getStatus() == SlotStatus.CORRECTED;

        log.debug("[QuestionPlanSupport] 槽位 {} 解决状态: {}, 值: {}, 状态: {}",
            slotCode, resolved, slotValue.getValue(), slotValue.getStatus());

        return resolved;
    }

    /**
     * 使用 LLM 智能选择紧急槽位（方案6）
     * 当 candidateGaps 为空时调用，LLM 评估每个候选槽位的重要性
     * 如果所有槽位分数都低于阈值，返回空计划（触发报告生成）
     */
    public QuestionPlan selectEmergencySlotByLLM(TriageContext context, TriageModelGateway modelGateway) {
        log.warn("[QuestionPlanSupport] 启动 LLM 智能兜底槽位选择（方案6）");

        // 1. 收集候选槽位
        List<SlotCode> candidateSlots = collectCandidateSlots(context);
        if (candidateSlots.isEmpty()) {
            log.warn("[QuestionPlanSupport] 没有可用的候选槽位，返回空计划");
            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of())
                .priorityReason("所有兜底槽位都已填充，建议生成报告")
                .build();
        }

        log.info("[QuestionPlanSupport] 收集到  个候选槽位: {}", candidateSlots.size(), candidateSlots);

        // 2. 构建 LLM 评分提示词
        String userPrompt = buildSlotScoringPrompt(context, candidateSlots);

        // 3. 调用 LLM 进行评分
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(SLOT_SCORING_SYSTEM_PROMPT));
            messages.add(ChatMessage.user(userPrompt));

            String response = modelGateway.chatWithReportModel(messages, 0.3D, 0.5D, 800);
            log.info("[QuestionPlanSupport] LLM 槽位评分响应: {}", response);

            // 4. 解析 LLM 响应
            SlotScoringResult scoringResult = parseSlotScoringResponse(response);

            if (scoringResult == null) {
                log.error("[QuestionPlanSupport] LLM 响应解析失败，使用默认兜底");
                return fallbackToDefaultSlot(context, candidateSlots);
            }

            log.info("[QuestionPlanSupport] LLM 推荐: {}, 理由: {}",
                scoringResult.getRecommendation(), scoringResult.getRationale());

            // 5. 根据 LLM 推荐决策
            if ("generate_report".equals(scoringResult.getRecommendation())) {
                log.info("[QuestionPlanSupport] LLM 建议生成报告，返回空计划");
                return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .priorityReason("LLM 评估：" + scoringResult.getRationale())
                    .build();
            }

            // 6. 选择得分最高的槽位
            SlotScore topScore = scoringResult.getScores().stream()
                .max(Comparator.comparingInt(SlotScore::getScore))
                .orElse(null);

            if (topScore == null || topScore.getScore() < SLOT_SCORE_THRESHOLD) {
                log.info("[QuestionPlanSupport] 最高分槽位 {} 分数 {} 低于阈值 {}，返回空计划",
                    topScore == null ? "null" : topScore.getSlot(),
                    topScore == null ? 0 : topScore.getScore(),
                    SLOT_SCORE_THRESHOLD);
                return QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .priorityReason("所有候选槽位分数都低于阈值，建议生成报告")
                    .build();
            }

            log.info("[QuestionPlanSupport] 选择槽位 {} (分数: {}, 理由: {})",
                topScore.getSlot(), topScore.getScore(), topScore.getReason());

            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of(topScore.getSlot()))
                .priorityReason("LLM 智能选择：" + topScore.getReason() + "（分数：" + topScore.getScore() + "）")
                .build();

        } catch (Exception e) {
            log.error("[QuestionPlanSupport] LLM 槽位评分失败", e);
            return fallbackToDefaultSlot(context, candidateSlots);
        }
    }

    /**
     * 收集候选槽位（16个兜底槽位，排除已填充的）
     */
    private List<SlotCode> collectCandidateSlots(TriageContext context) {
        List<SlotCode> allFallbackSlots = List.of(
            // 时间维度
            SlotCode.DURATION, SlotCode.ONSET_TIME,

            // 疼痛维度
            SlotCode.PAIN_SEVERITY, SlotCode.PAIN_CHARACTER, SlotCode.BODY_PART,

            // 伴随症状
            SlotCode.FEVER_PRESENCE, SlotCode.NAUSEA_PRESENCE, SlotCode.VOMITING_PRESENCE,
            SlotCode.DIARRHEA_PRESENCE, SlotCode.COUGH_PRESENCE, SlotCode.DYSPNEA_PRESENCE,

            // 病史
            SlotCode.DIAGNOSIS_HISTORY, SlotCode.MEDICATION_HISTORY,

            // 其他
            SlotCode.ASSOCIATED_SYMPTOMS, SlotCode.AGE
        );

        SlotState slotState = context.getSlotState();
        if (slotState == null) {
            return allFallbackSlots;
        }

        // 过滤掉已填充的槽位
        return allFallbackSlots.stream()
            .filter(slot -> {
                SlotValue slotValue = slotState.get(slot);
                return slotValue == null
                    || slotValue.getStatus() == null
                    || slotValue.getStatus() == SlotStatus.UNKNOWN;
            })
            .collect(Collectors.toList());
    }

    /**
     * 构建槽位评分提示词
     */
    private String buildSlotScoringPrompt(TriageContext context, List<SlotCode> candidateSlots) {
        StringBuilder prompt = new StringBuilder();

        // 1. 当前症状描述
        prompt.append("【当前症状描述】\n");
        prompt.append("用户输入：").append(context.getUserInput() == null ? "无" : context.getUserInput()).append("\n\n");

        // 2. 已提取的结构化症状
        prompt.append("【已提取的结构化症状】\n");
        if (context.getExtractedSymptoms() == null || context.getExtractedSymptoms().isEmpty()) {
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

        // 3. 已填充的槽位
        prompt.append("【已收集的信息】\n");
        SlotState slotState = context.getSlotState();
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

        // 4. 风险评估
        prompt.append("【当前风险评估】\n");
        RiskLevel riskLevel = context.getRiskAssessment();
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

        // 5. 候选槽位列表
        prompt.append("【候选问题槽位】\n");
        for (SlotCode slot : candidateSlots) {
            prompt.append("- ").append(slot.name()).append("：").append(getSlotDescription(slot)).append("\n");
        }
        prompt.append("\n");

        prompt.append("请根据以上信息，评估每个候选槽位的重要性（0-100分），并给出是否继续询问的建议。");

        return prompt.toString();
    }

    /**
     * 获取槽位的中文描述
     */
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

    /**
     * 解析 LLM 槽位评分响应
     */
    private SlotScoringResult parseSlotScoringResponse(String response) {
        if (response == null || response.isBlank()) {
            log.error("[QuestionPlanSupport] LLM 响应为空");
            return null;
        }

        try {
            // 提取 JSON（处理可能的 ```json``` 包装）
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
            jsonStr = jsonStr.trim();

            // 解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonStr);

            // 解析 scores
            List<SlotScore> scores = new ArrayList<>();
            JsonNode scoresNode = root.get("scores");
            if (scoresNode != null && scoresNode.isArray()) {
                for (JsonNode scoreNode : scoresNode) {
                    String slotName = scoreNode.get("slot").asText();
                    int score = scoreNode.get("score").asInt();
                    String reason = scoreNode.get("reason").asText();

                    try {
                        SlotCode slotCode = SlotCode.valueOf(slotName);
                        scores.add(SlotScore.builder()
                            .slot(slotCode)
                            .score(score)
                            .reason(reason)
                            .build());
                    } catch (IllegalArgumentException e) {
                        log.warn("[QuestionPlanSupport] 无法识别槽位代码: {}", slotName);
                    }
                }
            }

            // 解析 recommendation 和 rationale
            String recommendation = root.get("recommendation").asText();
            String rationale = root.get("rationale").asText();

            return SlotScoringResult.builder()
                .scores(scores)
                .recommendation(recommendation)
                .rationale(rationale)
                .build();

        } catch (Exception e) {
            log.error("[QuestionPlanSupport] 解析 LLM 响应失败", e);
            return null;
        }
    }

    /**
     * 默认兜底：选择第一个未填充的槽位
     */
    private QuestionPlan fallbackToDefaultSlot(TriageContext context, List<SlotCode> candidateSlots) {
        log.warn("[QuestionPlanSupport] LLM 评分失败，使用默认兜底策略");

        if (candidateSlots.isEmpty()) {
            return QuestionPlan.builder()
                .nextSlotsToAsk(List.of())
                .priorityReason("没有可用的候选槽位")
                .build();
        }

        SlotCode firstSlot = candidateSlots.get(0);
        log.info("[QuestionPlanSupport] 默认选择第一个槽位: {}", firstSlot);

        return QuestionPlan.builder()
            .nextSlotsToAsk(List.of(firstSlot))
            .priorityReason("默认兜底：选择 " + getSlotDescription(firstSlot))
            .build();
    }

    private static GapSpec gapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { return new GapSpec(slot, gapType, source, priority, reason); }
    private record GapRule(String semanticSignal, RiskSignalType riskSignalType, List<GapSpec> gapSpecs) { private static GapRule forSemanticSignal(String semanticSignal, List<GapSpec> gapSpecs) { return new GapRule(semanticSignal, null, gapSpecs); } private static GapRule forRiskSignal(RiskSignalType riskSignalType, GapSpec gapSpec) { return new GapRule(null, riskSignalType, gapSpec == null ? List.of() : List.of(gapSpec)); } }
    private record GapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {}

    @Data
    @Builder
    public static class SlotScoringResult {
        private List<SlotScore> scores;
        private String recommendation; // "continue" or "generate_report"
        private String rationale;
    }

    @Data
    @Builder
    public static class SlotScore {
        private SlotCode slot;
        private int score;
        private String reason;
    }
}
