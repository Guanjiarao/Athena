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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class QuestionPlanSupport {
    private static final List<GapRule> ROUTINE_RULES = List.of(
            GapRule.forSemanticSignal("腹痛", List.of(
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 78, "腹痛场景优先确认疼痛部位。"),
                    gapSpec(SlotCode.PAIN_SEVERITY, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 76, "腹痛场景还需确认疼痛程度。"),
                    gapSpec(SlotCode.FEVER_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 70, "腹痛场景需要确认是否伴随发热。"),
                    gapSpec(SlotCode.NAUSEA_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 68, "腹痛场景需要确认是否伴随恶心。"),
                    gapSpec(SlotCode.VOMITING_PRESENCE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 66, "腹痛场景需要确认是否伴随呕吐。"))),
            GapRule.forSemanticSignal("胸痛", List.of(
                    gapSpec(SlotCode.DYSPNEA_PRESENCE, QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY, 95, "胸痛场景需优先确认呼吸困难等高危信号。"),
                    gapSpec(SlotCode.BODY_PART, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 72, "胸痛场景仍需确认具体部位。"))),
            GapRule.forSemanticSignal("发热", List.of(
                    gapSpec(SlotCode.TEMPERATURE, QuestionGapType.FOLLOW_UP_REQUIRED, QuestionGapSource.PATTERN, 75, "发热场景优先确认体温。"))));

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
        SlotState slotState = context.getSlotState();
        List<QuestionGap> gaps = new ArrayList<>();
        addPrimaryComplaintGap(gaps, slotState);
        addRoutineGaps(gaps, context, slotState);
        addRiskDrivenGaps(gaps, context, slotState);
        addRiskDecisionDrivenGaps(gaps, context, slotState);
        deduplicateGaps(gaps);
        gaps.sort(Comparator.comparing(QuestionGap::getPriority).reversed());
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
        if (!isResolved(slotState, SlotCode.DURATION)) gaps.add(buildGap(SlotCode.DURATION, QuestionGapType.MISSING, QuestionGapSource.ROUTINE_POLICY, 80, "持续时间是基础病程信息，优先补齐。"));
        for (GapRule rule : ROUTINE_RULES) {
            if (rule == null || rule.gapSpecs() == null || !hasRoutineSemanticSignal(context, slotState, rule.semanticSignal())) continue;
            for (GapSpec gapSpec : rule.gapSpecs()) if (gapSpec != null) addIfMissing(gaps, slotState, gapSpec.slot(), gapSpec.gapType(), gapSpec.source(), gapSpec.priority(), gapSpec.reason());
        }
    }

    private void addRiskDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        if (context.getRiskSignalState() == null || context.getRiskSignalState().isEmpty()) return;
        for (GapRule rule : RISK_RULES) {
            if (rule == null || rule.gapSpecs() == null || rule.gapSpecs().isEmpty() || !hasPositiveRiskSignal(context, rule.riskSignalType())) continue;
            GapSpec gapSpec = rule.gapSpecs().get(0);
            addIfMissing(gaps, slotState, gapSpec.slot(), gapSpec.gapType(), gapSpec.source(), gapSpec.priority(), gapSpec.reason());
        }
    }

    private void addRiskDecisionDrivenGaps(List<QuestionGap> gaps, TriageContext context, SlotState slotState) {
        if (context == null || context.getRiskDecision() == null || context.getRiskDecision().getUnresolvedRiskGaps() == null) return;
        for (var riskGap : context.getRiskDecision().getUnresolvedRiskGaps()) {
            if (riskGap == null || riskGap.getSlot() == null) continue;
            addIfMissing(gaps, slotState, riskGap.getSlot(), QuestionGapType.RISK_REQUIRED, QuestionGapSource.RISK_POLICY,
                    riskGap.getPriority() == null ? 98 : riskGap.getPriority(),
                    riskGap.getReason() == null || riskGap.getReason().isBlank() ? "当前存在 unresolved risk gap，应继续优先确认。" : riskGap.getReason());
        }
    }

    private boolean hasRoutineSemanticSignal(TriageContext context, SlotState slotState, String semanticSignal) {
        if (semanticSignal == null || semanticSignal.isBlank()) return false;
        String primarySymptom = slotValue(slotState, SlotCode.PRIMARY_SYMPTOM);
        if (semanticSignal.equals(primarySymptom)) return true;
        if (context != null && context.getExtractedSymptoms() != null) {
            boolean matchedStructuredSymptom = context.getExtractedSymptoms().stream().anyMatch(symptom -> symptom != null && semanticSignal.equals(symptom.getName()));
            if (matchedStructuredSymptom) return true;
        }
        if ("胸痛".equals(semanticSignal) && hasPositiveRiskSignal(context, RiskSignalType.CHEST_PAIN)) return true;
        return semanticSignalResolver.hasPrimarySignalFact(context, semanticSignal);
    }

    private boolean hasPositiveRiskSignal(TriageContext context, RiskSignalType signalType) {
        if (context == null || signalType == null || context.getRiskSignalState() == null) return false;
        return context.getRiskSignalState().stream().anyMatch(signal -> signal != null && signal.getType() == signalType && signal.getAssertion() == AssertionStatus.PRESENT);
    }

    private String slotValue(SlotState slotState, SlotCode slotCode) { SlotValue slotValue = slotState == null ? null : slotState.get(slotCode); return slotValue == null ? null : slotValue.getValue(); }
    private void addIfMissing(List<QuestionGap> gaps, SlotState slotState, SlotCode slotCode, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { if (!isResolved(slotState, slotCode)) gaps.add(buildGap(slotCode, gapType, source, priority, reason)); }
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

    private void deduplicateGaps(List<QuestionGap> gaps) { LinkedHashSet<SlotCode> seen = new LinkedHashSet<>(); gaps.removeIf(gap -> gap == null || gap.getSlot() == null || !seen.add(gap.getSlot())); }
    private boolean isResolved(SlotState slotState, SlotCode slotCode) { SlotValue slotValue = slotState.get(slotCode); if (slotValue == null || slotValue.getStatus() == null) return false; return slotValue.getStatus() == SlotStatus.FILLED || slotValue.getStatus() == SlotStatus.NEGATED || slotValue.getStatus() == SlotStatus.CORRECTED || slotValue.getStatus() == SlotStatus.INFERRED; }
    private static GapSpec gapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) { return new GapSpec(slot, gapType, source, priority, reason); }
    private record GapRule(String semanticSignal, RiskSignalType riskSignalType, List<GapSpec> gapSpecs) { private static GapRule forSemanticSignal(String semanticSignal, List<GapSpec> gapSpecs) { return new GapRule(semanticSignal, null, gapSpecs); } private static GapRule forRiskSignal(RiskSignalType riskSignalType, GapSpec gapSpec) { return new GapRule(null, riskSignalType, gapSpec == null ? List.of() : List.of(gapSpec)); } }
    private record GapSpec(SlotCode slot, QuestionGapType gapType, QuestionGapSource source, int priority, String reason) {}
}
