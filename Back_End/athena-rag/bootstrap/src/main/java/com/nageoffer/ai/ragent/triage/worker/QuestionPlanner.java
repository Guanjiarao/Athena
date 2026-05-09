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

import com.nageoffer.ai.ragent.triage.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class QuestionPlanner {
    private final QuestionPlanSupport questionPlanSupport;
    public QuestionPlanner(QuestionPlanSupport questionPlanSupport) { this.questionPlanSupport = questionPlanSupport; }

    public TriageContext execute(TriageContext context) {
        if (context == null) context = new TriageContext();
        context.ensureCollections();
        List<QuestionGap> candidateGaps = questionPlanSupport.determineQuestionGaps(context);
        List<QuestionNeed> questionNeeds = questionPlanSupport.determineQuestionNeeds(context);
        List<AskabilityDecision> askabilityDecisions = evaluateAskability(context, candidateGaps);
        List<QuestionGap> askableGaps = new ArrayList<>(), suppressedGaps = new ArrayList<>();
        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) continue;
            AskabilityDecision decision = askabilityDecisions.stream().filter(each -> each != null && each.getSlot() == gap.getSlot()).findFirst().orElse(null);
            if (decision != null && Boolean.TRUE.equals(decision.getAskable())) { gap.setAskable(Boolean.TRUE); askableGaps.add(gap); }
            else { gap.setAskable(Boolean.FALSE); suppressedGaps.add(gap); }
        }
        PolicySelection selection = selectGapsByPolicy(context, askableGaps);
        List<QuestionGap> selectedGaps = selection.selectedGaps();
        Set<SlotCode> selectedSlots = selectedGaps.stream().map(QuestionGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
        for (QuestionGap gap : askableGaps) if (!selectedSlots.contains(gap.getSlot())) { gap.setAskable(Boolean.FALSE); suppressedGaps.add(gap); askabilityDecisions.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.BLOCKED_BY_POLICY).detail("当前轮策略未选择该 gap：" + selection.policyReason()).build()); }
        List<SlotCode> pendingSlots = selectedGaps.stream().map(QuestionGap::getSlot).distinct().toList();
        context.setCandidateQuestionGaps(new ArrayList<>(candidateGaps));
        context.setSelectedQuestionGaps(new ArrayList<>(selectedGaps));
        context.setSuppressedQuestionGaps(new ArrayList<>(suppressedGaps));
        context.setAskabilityDecisions(new ArrayList<>(askabilityDecisions));
        context.setPendingSlots(new ArrayList<>(pendingSlots));
        context.setLastAskedSlots(new ArrayList<>(pendingSlots));
        context.setQuestionPlan(QuestionPlan.builder().questionNeeds(new ArrayList<>(questionNeeds)).selectedQuestionGaps(new ArrayList<>(selectedGaps)).suppressedQuestionGaps(new ArrayList<>(suppressedGaps)).askabilityDecisions(new ArrayList<>(askabilityDecisions)).nextSlotsToAsk(new ArrayList<>(pendingSlots)).pendingSlots(new ArrayList<>(pendingSlots)).askCount(pendingSlots.size()).followUpMode(!pendingSlots.isEmpty()).priorityReason(questionPlanSupport.buildPriorityReason(selectedGaps.isEmpty() ? candidateGaps : selectedGaps)).policyReason(selection.policyReason()).build());
        return context;
    }

    private List<AskabilityDecision> evaluateAskability(TriageContext context, List<QuestionGap> candidateGaps) {
        List<AskabilityDecision> result = new ArrayList<>();
        List<SlotCode> answeredSlots = context.getAnsweredSlots() == null ? List.of() : context.getAnsweredSlots();
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);
        if (candidateGaps == null || candidateGaps.isEmpty()) return result;
        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) continue;
            SlotValue slotValue = context.getSlotState() == null ? null : context.getSlotState().get(gap.getSlot());
            if (unresolvedRiskSlots.contains(gap.getSlot())) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("该槽位对应 unresolved risk gap，应允许持续追问直到闭合。").build());
                continue;
            }
            if (answeredSlots.contains(gap.getSlot())) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.ANSWERED_ALREADY).detail("当前轮已回答该槽位，无需重复提问。").build());
                continue;
            }
            if (slotValue != null && slotValue.getStatus() == SlotStatus.NEGATED) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.NEGATED_ALREADY).detail("该槽位已被明确否定，当前不再重复追问。").build());
                continue;
            }
            if (slotValue != null && slotValue.getStatus() == SlotStatus.CORRECTED) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.CORRECTED_ALREADY).detail("该槽位已被纠正并稳定落槽，当前不再重复追问。").build());
                continue;
            }
            if (slotValue != null && slotValue.getStatus() == SlotStatus.INFERRED && gap.getGapType() != QuestionGapType.RISK_REQUIRED) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.BLOCKED_BY_POLICY).detail("该槽位当前是推断值，非风险场景下暂不优先重问。").build());
                continue;
            }
            if (slotValue != null && slotValue.getStatus() == SlotStatus.CONFLICTING) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("该槽位存在冲突信息，应允许优先重问以消解冲突。").build());
                continue;
            }
            result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("当前 gap 仍可提问，等待 policy 选择。").build());
        }
        return result;
    }

    private PolicySelection selectGapsByPolicy(TriageContext context, List<QuestionGap> askableGaps) {
        if (askableGaps == null || askableGaps.isEmpty()) return new PolicySelection(List.of(), "当前无可提问 gap。");
        List<QuestionGap> sorted = askableGaps.stream().sorted(Comparator.comparing(QuestionGap::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        RiskDecision riskDecision = context.getRiskDecision();
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);
        Set<SlotCode> confirmedRiskSlots = riskDecision == null || riskDecision.getConfirmedRiskGaps() == null ? Set.of() : riskDecision.getConfirmedRiskGaps().stream().map(RiskGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
        List<QuestionGap> unresolvedRiskGaps = sorted.stream().filter(gap -> unresolvedRiskSlots.contains(gap.getSlot())).limit(1).toList();
        if (!unresolvedRiskGaps.isEmpty()) return new PolicySelection(unresolvedRiskGaps, "当前存在未闭合 risk gap，本轮优先单问对应风险槽位。");
        List<QuestionGap> confirmedRiskGaps = sorted.stream().filter(gap -> confirmedRiskSlots.contains(gap.getSlot()) || isRiskGap(gap)).limit(1).toList();
        if (!confirmedRiskGaps.isEmpty() && riskDecision != null && riskDecision.getDecisionType() == RiskDecisionType.TRIGGER_WARNING) return new PolicySelection(confirmedRiskGaps, "当前已确认高风险信号，本轮仅允许单问最高优先级风险问题。");
        List<QuestionGap> conflictingGaps = sorted.stream().filter(gap -> isConflictingGap(context, gap)).limit(1).toList();
        if (!conflictingGaps.isEmpty()) return new PolicySelection(conflictingGaps, "当前存在槽位冲突，本轮优先单问冲突消解问题。");
        List<QuestionGap> primarySymptomGaps = sorted.stream().filter(gap -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM).limit(1).toList();
        if (!primarySymptomGaps.isEmpty()) return new PolicySelection(primarySymptomGaps, "当前主诉仍未明确，本轮优先单问主诉澄清 gap。");
        return new PolicySelection(sorted.stream().limit(1).toList(), "按最高优先级 gap 继续追问。");
    }

    private Set<SlotCode> unresolvedRiskSlots(TriageContext context) {
        RiskDecision riskDecision = context == null ? null : context.getRiskDecision();
        return riskDecision == null || riskDecision.getUnresolvedRiskGaps() == null ? Set.of() : riskDecision.getUnresolvedRiskGaps().stream().map(RiskGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private boolean isRiskGap(QuestionGap gap) { return gap != null && gap.getGapType() == QuestionGapType.RISK_REQUIRED; }
    private boolean isConflictingGap(TriageContext context, QuestionGap gap) { SlotValue slotValue = context == null || context.getSlotState() == null || gap == null ? null : context.getSlotState().get(gap.getSlot()); return slotValue != null && slotValue.getStatus() == SlotStatus.CONFLICTING; }
    private record PolicySelection(List<QuestionGap> selectedGaps, String policyReason) {}
}
