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
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionPlanner {
    private final QuestionPlanSupport questionPlanSupport;
    private final TriageModelGateway triageModelGateway;

    public QuestionPlanner(QuestionPlanSupport questionPlanSupport, TriageModelGateway triageModelGateway) {
        this.questionPlanSupport = questionPlanSupport;
        this.triageModelGateway = triageModelGateway;
    }

    public TriageContext execute(TriageContext context) {
        log.info("[QuestionPlanner] 开始规划下一轮问题, sessionId={}, turnCount={}",
            context.getSessionId(), context.getTotalTurnCount());

        if (context == null) context = new TriageContext();
        context.ensureCollections();

        List<QuestionGap> candidateGaps = questionPlanSupport.determineQuestionGaps(context);
        log.info("[QuestionPlanner] 获得 {} 个 candidate gaps", candidateGaps.size());

        // 如果 candidateGaps 为空，启动 LLM 智能兜底（方案6）
        if (candidateGaps.isEmpty()) {
            log.warn("[QuestionPlanner] candidateGaps 为空，启动 LLM 智能兜底槽位选择");
            QuestionPlan emergencyPlan = questionPlanSupport.selectEmergencySlotByLLM(context, triageModelGateway);

            if (emergencyPlan.getNextSlotsToAsk().isEmpty()) {
                log.info("[QuestionPlanner] LLM 建议生成报告: {}", emergencyPlan.getPriorityReason());
                context.setQuestionPlan(emergencyPlan);
                return context;
            }

            log.info("[QuestionPlanner] LLM 选择紧急槽位: {}", emergencyPlan.getNextSlotsToAsk());
            context.setQuestionPlan(emergencyPlan);
            return context;
        }

        List<QuestionNeed> questionNeeds = questionPlanSupport.determineQuestionNeeds(context);

        List<AskabilityDecision> askabilityDecisions = evaluateAskability(context, candidateGaps);
        log.info("[QuestionPlanner] 可询问性评估完成, 决策数量: {}", askabilityDecisions.size());

        List<QuestionGap> askableGaps = new ArrayList<>(), suppressedGaps = new ArrayList<>();
        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) continue;
            AskabilityDecision decision = askabilityDecisions.stream().filter(each -> each != null && each.getSlot() == gap.getSlot()).findFirst().orElse(null);
            if (decision != null && Boolean.TRUE.equals(decision.getAskable())) { gap.setAskable(Boolean.TRUE); askableGaps.add(gap); }
            else { gap.setAskable(Boolean.FALSE); suppressedGaps.add(gap); }
        }

        PolicySelection selection = selectGapsByPolicy(context, askableGaps);
        List<QuestionGap> selectedGaps = selection.selectedGaps();
        log.info("[QuestionPlanner] 策略选择后 gaps 数量: {}, gaps: {}",
            selectedGaps.size(),
            selectedGaps.stream().map(g -> g.getSlot().toString()).collect(Collectors.toList()));

        Set<SlotCode> selectedSlots = selectedGaps.stream().map(QuestionGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
        for (QuestionGap gap : askableGaps) if (!selectedSlots.contains(gap.getSlot())) { gap.setAskable(Boolean.FALSE); suppressedGaps.add(gap); askabilityDecisions.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.BLOCKED_BY_POLICY).detail("当前轮策略未选择该 gap：" + selection.policyReason()).build()); }
        List<SlotCode> pendingSlots = selectedGaps.stream().map(QuestionGap::getSlot).distinct().toList();
        context.setCandidateQuestionGaps(new ArrayList<>(candidateGaps));
        context.setSelectedQuestionGaps(new ArrayList<>(selectedGaps));
        context.setSuppressedQuestionGaps(new ArrayList<>(suppressedGaps));
        context.setAskabilityDecisions(new ArrayList<>(askabilityDecisions));
        context.setPendingSlots(new ArrayList<>(pendingSlots));

        // 累积 lastAskedSlots，保持最近 3 轮的记录
        List<SlotCode> lastAskedSlots = context.getLastAskedSlots() == null ? new ArrayList<>() : new ArrayList<>(context.getLastAskedSlots());
        lastAskedSlots.addAll(pendingSlots);
        // 保持最近 9 个槽位（约 3 轮，每轮最多 3 个槽位）
        while (lastAskedSlots.size() > 9) {
            lastAskedSlots.remove(0);
        }
        context.setLastAskedSlots(lastAskedSlots);
        context.setQuestionPlan(QuestionPlan.builder().questionNeeds(new ArrayList<>(questionNeeds)).selectedQuestionGaps(new ArrayList<>(selectedGaps)).suppressedQuestionGaps(new ArrayList<>(suppressedGaps)).askabilityDecisions(new ArrayList<>(askabilityDecisions)).nextSlotsToAsk(new ArrayList<>(pendingSlots)).pendingSlots(new ArrayList<>(pendingSlots)).askCount(pendingSlots.size()).followUpMode(!pendingSlots.isEmpty()).priorityReason(questionPlanSupport.buildPriorityReason(selectedGaps.isEmpty() ? candidateGaps : selectedGaps)).policyReason(selection.policyReason()).build());

        log.info("[QuestionPlanner] 规划完成, pendingSlots: {}", pendingSlots);

        return context;
    }

    private List<AskabilityDecision> evaluateAskability(TriageContext context, List<QuestionGap> candidateGaps) {
        log.info("[QuestionPlanner] 开始评估可询问性, gaps 数量: {}", candidateGaps.size());

        List<AskabilityDecision> result = new ArrayList<>();
        Set<SlotCode> answeredSlots = context.getAnsweredSlots() == null
            ? Set.of()
            : new HashSet<>(context.getAnsweredSlots());
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);

        // 新增：获取最近询问过的槽位
        Set<SlotCode> recentlyAskedSlots = context.getLastAskedSlots() == null
            ? Set.of()
            : new HashSet<>(context.getLastAskedSlots());

        log.info("[QuestionPlanner] answeredSlots: {}, unresolvedRiskSlots: {}, recentlyAskedSlots: {}",
            answeredSlots, unresolvedRiskSlots, recentlyAskedSlots);

        if (candidateGaps == null || candidateGaps.isEmpty()) return result;

        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) continue;

            SlotValue slotValue = context.getSlotState() == null ? null : context.getSlotState().get(gap.getSlot());

            if (unresolvedRiskSlots.contains(gap.getSlot())) {
                log.info("[QuestionPlanner] 槽位 {} 是 unresolved risk gap，允许询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("该槽位对应 unresolved risk gap，应允许持续追问直到闭合。").build());
                continue;
            }

            if (answeredSlots.contains(gap.getSlot())) {
                log.info("[QuestionPlanner] 槽位 {} 在当前轮已回答，抑制询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.ANSWERED_ALREADY).detail("当前轮已回答该槽位，无需重复提问。").build());
                continue;
            }

            // 新增：如果最近3轮内已询问过，且不是风险槽位，抑制询问
            if (recentlyAskedSlots.contains(gap.getSlot())
                && gap.getGapType() != QuestionGapType.RISK_REQUIRED) {
                log.info("[QuestionPlanner] 槽位 {} 最近已询问过，抑制重复提问", gap.getSlot());
                result.add(AskabilityDecision.builder()
                    .slot(gap.getSlot())
                    .askable(Boolean.FALSE)
                    .reason(AskabilityReason.ANSWERED_ALREADY)
                    .detail("该槽位最近3轮内已询问，避免重复提问。")
                    .build());
                continue;
            }

            if (slotValue != null && slotValue.getStatus() == SlotStatus.FILLED) {
                log.info("[QuestionPlanner] 槽位 {} 状态为 FILLED，抑制询问", gap.getSlot());
                result.add(AskabilityDecision.builder()
                    .slot(gap.getSlot())
                    .askable(Boolean.FALSE)
                    .reason(AskabilityReason.ANSWERED_ALREADY)
                    .detail("该槽位已填充，无需重复提问。")
                    .build());
                continue;
            }

            if (slotValue != null && slotValue.getStatus() == SlotStatus.NEGATED) {
                log.info("[QuestionPlanner] 槽位 {} 状态为 NEGATED，抑制询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.NEGATED_ALREADY).detail("该槽位已被明确否定，当前不再重复追问。").build());
                continue;
            }

            if (slotValue != null && slotValue.getStatus() == SlotStatus.CORRECTED) {
                log.info("[QuestionPlanner] 槽位 {} 状态为 CORRECTED，抑制询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.CORRECTED_ALREADY).detail("该槽位已被纠正并稳定落槽，当前不再重复追问。").build());
                continue;
            }

            if (slotValue != null && slotValue.getStatus() == SlotStatus.INFERRED && gap.getGapType() != QuestionGapType.RISK_REQUIRED) {
                log.info("[QuestionPlanner] 槽位 {} 状态为 INFERRED 且非风险类型，抑制询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.BLOCKED_BY_POLICY).detail("该槽位当前是推断值，非风险场景下暂不优先重问。").build());
                continue;
            }

            if (slotValue != null && slotValue.getStatus() == SlotStatus.CONFLICTING) {
                log.info("[QuestionPlanner] 槽位 {} 状态为 CONFLICTING，允许询问", gap.getSlot());
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("该槽位存在冲突信息，应允许优先重问以消解冲突。").build());
                continue;
            }

            log.info("[QuestionPlanner] 槽位 {} 可以询问", gap.getSlot());
            result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("当前 gap 仍可提问，等待 policy 选择。").build());
        }

        return result;
    }

    private PolicySelection selectGapsByPolicy(TriageContext context, List<QuestionGap> askableGaps) {
        log.info("[QuestionPlanner] 开始策略选择, askableGaps 数量: {}", askableGaps == null ? 0 : askableGaps.size());

        if (askableGaps == null || askableGaps.isEmpty()) return new PolicySelection(List.of(), "当前无可提问 gap。");

        // askableGaps 已经在 evaluateAskability() 中过滤过了，直接使用

        // 按优先级排序
        List<QuestionGap> sorted = askableGaps.stream().sorted(Comparator.comparing(QuestionGap::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        RiskDecision riskDecision = context.getRiskDecision();
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);
        Set<SlotCode> confirmedRiskSlots = riskDecision == null || riskDecision.getConfirmedRiskGaps() == null ? Set.of() : riskDecision.getConfirmedRiskGaps().stream().map(RiskGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());

        log.info("[QuestionPlanner] unresolvedRiskSlots: {}, confirmedRiskSlots: {}", unresolvedRiskSlots, confirmedRiskSlots);

        List<QuestionGap> unresolvedRiskGaps = sorted.stream().filter(gap -> unresolvedRiskSlots.contains(gap.getSlot())).limit(1).toList();
        if (!unresolvedRiskGaps.isEmpty()) {
            log.info("[QuestionPlanner] 选择策略: 未闭合 risk gap, 选择槽位: {}", unresolvedRiskGaps.get(0).getSlot());
            return new PolicySelection(unresolvedRiskGaps, "当前存在未闭合 risk gap，本轮优先单问对应风险槽位。");
        }

        List<QuestionGap> confirmedRiskGaps = sorted.stream().filter(gap -> confirmedRiskSlots.contains(gap.getSlot()) || isRiskGap(gap)).limit(1).toList();
        if (!confirmedRiskGaps.isEmpty() && riskDecision != null && riskDecision.getDecisionType() == RiskDecisionType.TRIGGER_WARNING) {
            log.info("[QuestionPlanner] 选择策略: 已确认高风险信号, 选择槽位: {}", confirmedRiskGaps.get(0).getSlot());
            return new PolicySelection(confirmedRiskGaps, "当前已确认高风险信号，本轮仅允许单问最高优先级风险问题。");
        }

        List<QuestionGap> conflictingGaps = sorted.stream().filter(gap -> isConflictingGap(context, gap)).limit(1).toList();
        if (!conflictingGaps.isEmpty()) {
            log.info("[QuestionPlanner] 选择策略: 槽位冲突, 选择槽位: {}", conflictingGaps.get(0).getSlot());
            return new PolicySelection(conflictingGaps, "当前存在槽位冲突，本轮优先单问冲突消解问题。");
        }

        List<QuestionGap> primarySymptomGaps = sorted.stream().filter(gap -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM).limit(1).toList();
        if (!primarySymptomGaps.isEmpty()) {
            log.info("[QuestionPlanner] 选择策略: 主诉未明确, 选择槽位: PRIMARY_SYMPTOM");
            return new PolicySelection(primarySymptomGaps, "当前主诉仍未明确，本轮优先单问主诉澄清 gap。");
        }

        // 新增：ROUTINE_RULES 优先策略
        // 如果存在来自 ROUTINE_RULES 的高优先级 gaps（优先级 >= 70），优先选择它们
        // 这些 gaps 来自特定场景的规则（如"腹泻"、"胃疼"），应该优先于通用的临床推理顺序
        List<QuestionGap> routineGaps = sorted.stream()
                .filter(gap -> gap.getSource() == QuestionGapSource.PATTERN && gap.getPriority() != null && gap.getPriority() >= 70)
                .limit(1)
                .toList();
        if (!routineGaps.isEmpty()) {
            log.info("[QuestionPlanner] 选择策略: 常规优先级, 选择槽位: {}, 优先级: {}",
                routineGaps.get(0).getSlot(), routineGaps.get(0).getPriority());
            return new PolicySelection(routineGaps, "按照场景规则定义的优先级顺序提问。");
        }

        // 新增：核心槽位优先策略（按临床推理顺序排序）
        // 如果还没有询问完所有核心槽位，优先询问核心槽位
        if (!questionPlanSupport.hasAskedAllCoreSlots(context)) {
            List<SlotCode> coreSlots = questionPlanSupport.getCoreRequiredSlots();
            List<QuestionGap> coreGaps = sorted.stream()
                    .filter(gap -> coreSlots.contains(gap.getSlot()))
                    .sorted(Comparator.comparing(gap -> questionPlanSupport.getReasoningOrder(gap.getSlot())))
                    .limit(1)
                    .toList();
            if (!coreGaps.isEmpty()) {
                log.info("[QuestionPlanner] 选择策略: 核心必问槽位（临床推理顺序）, 选择槽位: {}, 推理顺序: {}",
                    coreGaps.get(0).getSlot(), questionPlanSupport.getReasoningOrder(coreGaps.get(0).getSlot()));
                return new PolicySelection(coreGaps, "优先询问核心必问槽位以确保基础信息完整（按临床推理顺序）。");
            }
        }

        log.info("[QuestionPlanner] 选择策略: 最高优先级, 选择槽位: {}", sorted.isEmpty() ? "无" : sorted.get(0).getSlot());
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
