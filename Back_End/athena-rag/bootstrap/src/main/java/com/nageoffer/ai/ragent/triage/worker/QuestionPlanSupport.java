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

import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapReasonType;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionNeed;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
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
    private static GapSpec gapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { return new GapSpec(slot, gapType, source, priority, reason); }
    private record GapRule(String semanticSignal, RiskSignalType riskSignalType, List<GapSpec> gapSpecs) { private static GapRule forSemanticSignal(String semanticSignal, List<GapSpec> gapSpecs) { return new GapRule(semanticSignal, null, gapSpecs); } private static GapRule forRiskSignal(RiskSignalType riskSignalType, GapSpec gapSpec) { return new GapRule(null, riskSignalType, gapSpec == null ? List.of() : List.of(gapSpec)); } }
    private record GapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {}
}
