

package com.nageoffer.ai.ragent.triage.question;

import com.nageoffer.ai.ragent.triage.model.AskabilityDecision;
import com.nageoffer.ai.ragent.triage.model.AskabilityReason;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.QuestionNeed;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskDecisionType;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionPlanner {
    private final QuestionPlanningSupport questionPlanSupport;
    private final ColdStartSlotSelector coldStartSlotSelector;

    public QuestionPlanner(QuestionPlanningSupport questionPlanSupport, ColdStartSlotSelector coldStartSlotSelector) {
        this.questionPlanSupport = questionPlanSupport;
        this.coldStartSlotSelector = coldStartSlotSelector;
    }

    public QuestionPlannerResult execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();

        QuestionPlannerResult result = new QuestionPlannerResult();
        List<QuestionGap> reducerCandidateGaps = context.getCandidateQuestionGaps() == null
            ? List.of()
            : new ArrayList<>(context.getCandidateQuestionGaps());
        List<QuestionGap> supportCandidateGaps = reducerCandidateGaps.isEmpty()
            ? questionPlanSupport.determineQuestionGaps(context)
            : List.of();
        List<QuestionGap> candidateGaps = reducerCandidateGaps.isEmpty()
            ? supportCandidateGaps
            : reducerCandidateGaps;

        result.setCandidateQuestionGaps(new ArrayList<>(candidateGaps));
        result.setLlmFallbackHistory(context.getLlmFallbackHistory() == null ? new ArrayList<>() : new ArrayList<>(context.getLlmFallbackHistory()));

        log.info("[QuestionPlanner] 开始规划下一轮问题, sessionId={}, turnCount={}, candidateGaps={}",
            context.getSessionId(), context.getTotalTurnCount(), candidateGaps.size());

        if (candidateGaps.isEmpty()) {
            return buildEmergencyFallbackResult(context, "candidateGaps 为空", result);
        }

        List<QuestionNeed> questionNeeds = questionPlanSupport.determineQuestionNeeds(context);
        List<AskabilityDecision> askabilityDecisions = evaluateAskability(context, candidateGaps);
        List<QuestionGap> askableGaps = new ArrayList<>();
        List<QuestionGap> suppressedGaps = new ArrayList<>();

        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) {
                continue;
            }
            AskabilityDecision decision = askabilityDecisions.stream()
                .filter(each -> each != null && each.getSlot() == gap.getSlot())
                .findFirst()
                .orElse(null);
            if (decision != null && Boolean.TRUE.equals(decision.getAskable())) {
                gap.setAskable(Boolean.TRUE);
                askableGaps.add(gap);
            } else {
                gap.setAskable(Boolean.FALSE);
                suppressedGaps.add(gap);
            }
        }

        PolicySelection selection = selectGapsByPolicy(context, askableGaps);
        List<QuestionGap> selectedGaps = selection.selectedGaps();
        if (selectedGaps.isEmpty() && !candidateGaps.isEmpty()) {
            return buildEmergencyFallbackResult(context, "candidateGaps 全部不可问", result);
        }

        Set<SlotCode> selectedSlots = selectedGaps.stream().map(QuestionGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
        for (QuestionGap gap : askableGaps) {
            if (!selectedSlots.contains(gap.getSlot())) {
                gap.setAskable(Boolean.FALSE);
                suppressedGaps.add(gap);
                askabilityDecisions.add(AskabilityDecision.builder()
                    .slot(gap.getSlot())
                    .askable(Boolean.FALSE)
                    .reason(AskabilityReason.BLOCKED_BY_POLICY)
                    .detail("当前轮策略未选择该 gap：" + selection.policyReason())
                    .build());
            }
        }

        List<SlotCode> pendingSlots = selectedGaps.stream().map(QuestionGap::getSlot).distinct().toList();
        List<SlotCode> lastAskedSlots = context.getLastAskedSlots() == null ? new ArrayList<>() : new ArrayList<>(context.getLastAskedSlots());
        lastAskedSlots.addAll(pendingSlots);
        while (lastAskedSlots.size() > 9) {
            lastAskedSlots.remove(0);
        }

        QuestionPlan questionPlan = QuestionPlan.builder()
            .questionNeeds(new ArrayList<>(questionNeeds))
            .selectedQuestionGaps(new ArrayList<>(selectedGaps))
            .suppressedQuestionGaps(new ArrayList<>(suppressedGaps))
            .askabilityDecisions(new ArrayList<>(askabilityDecisions))
            .nextSlotsToAsk(new ArrayList<>(pendingSlots))
            .pendingSlots(new ArrayList<>(pendingSlots))
            .askCount(pendingSlots.size())
            .followUpMode(!pendingSlots.isEmpty())
            .priorityReason(questionPlanSupport.buildPriorityReason(selectedGaps.isEmpty() ? candidateGaps : selectedGaps))
            .policyReason(selection.policyReason())
            .build();

        result.setQuestionPlan(questionPlan);
        result.setSelectedQuestionGaps(new ArrayList<>(selectedGaps));
        result.setSuppressedQuestionGaps(new ArrayList<>(suppressedGaps));
        result.setAskabilityDecisions(new ArrayList<>(askabilityDecisions));
        result.setPendingSlots(new ArrayList<>(pendingSlots));
        result.setLastAskedSlots(lastAskedSlots);
        result.setLlmFallbackTriggered(false);
        return result;
    }


    private int countConsecutiveLLMFallbacks(TriageContext context) {
        if (context == null || context.getLlmFallbackHistory() == null || context.getLlmFallbackHistory().isEmpty()) {
            return 0;
        }
        int count = 0;
        List<Boolean> history = context.getLlmFallbackHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (Boolean.TRUE.equals(history.get(i))) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private List<AskabilityDecision> evaluateAskability(TriageContext context, List<QuestionGap> candidateGaps) {
        List<AskabilityDecision> result = new ArrayList<>();
        Set<SlotCode> answeredSlots = context.getAnsweredSlots() == null ? Set.of() : new HashSet<>(context.getAnsweredSlots());
        Set<SlotCode> recentlyAskedSlots = context.getLastAskedSlots() == null ? Set.of() : new HashSet<>(context.getLastAskedSlots());
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);

        for (QuestionGap gap : candidateGaps) {
            if (gap == null || gap.getSlot() == null) {
                continue;
            }
            SlotValue slotValue = context.getSlotState() == null ? null : context.getSlotState().get(gap.getSlot());
            if (unresolvedRiskSlots.contains(gap.getSlot())) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.TRUE).reason(AskabilityReason.ASKABLE).detail("该槽位对应 unresolved risk gap，应允许持续追问直到闭合。").build());
                continue;
            }
            if (answeredSlots.contains(gap.getSlot())) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.ANSWERED_ALREADY).detail("当前轮已回答该槽位，无需重复提问。").build());
                continue;
            }
            if (recentlyAskedSlots.contains(gap.getSlot()) && gap.getGapType() != QuestionGapType.RISK_REQUIRED) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.ANSWERED_ALREADY).detail("该槽位最近3轮内已询问，避免重复提问。").build());
                continue;
            }
            if (slotValue != null && slotValue.getStatus() == SlotStatus.FILLED) {
                result.add(AskabilityDecision.builder().slot(gap.getSlot()).askable(Boolean.FALSE).reason(AskabilityReason.ANSWERED_ALREADY).detail("该槽位已填充，无需重复提问。").build());
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
        if (askableGaps == null || askableGaps.isEmpty()) {
            return new PolicySelection(List.of(), "当前无可提问 gap。");
        }
        List<QuestionGap> sorted = askableGaps.stream()
            .sorted(Comparator.comparing(QuestionGap::getPriority, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        RiskDecision riskDecision = context.getRiskDecision();
        Set<SlotCode> unresolvedRiskSlots = unresolvedRiskSlots(context);
        Set<SlotCode> confirmedRiskSlots = riskDecision == null || riskDecision.getConfirmedRiskGaps() == null
            ? Set.of()
            : riskDecision.getConfirmedRiskGaps().stream().map(RiskGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());

        List<QuestionGap> unresolvedRiskGaps = sorted.stream().filter(gap -> unresolvedRiskSlots.contains(gap.getSlot())).limit(1).toList();
        if (!unresolvedRiskGaps.isEmpty()) {
            return new PolicySelection(unresolvedRiskGaps, "当前存在未闭合 risk gap，本轮优先单问对应风险槽位。");
        }

        List<QuestionGap> confirmedRiskGaps = sorted.stream().filter(gap -> confirmedRiskSlots.contains(gap.getSlot()) || isRiskGap(gap)).limit(1).toList();
        if (!confirmedRiskGaps.isEmpty() && riskDecision != null && riskDecision.getDecisionType() == RiskDecisionType.TRIGGER_WARNING) {
            return new PolicySelection(confirmedRiskGaps, "当前已确认高风险信号，本轮仅允许单问最高优先级风险问题。");
        }

        List<QuestionGap> conflictingGaps = sorted.stream().filter(gap -> isConflictingGap(context, gap)).limit(1).toList();
        if (!conflictingGaps.isEmpty()) {
            return new PolicySelection(conflictingGaps, "当前存在槽位冲突，本轮优先单问冲突消解问题。");
        }

        List<QuestionGap> primarySymptomGaps = sorted.stream().filter(gap -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM).limit(1).toList();
        if (!primarySymptomGaps.isEmpty()) {
            return new PolicySelection(primarySymptomGaps, "当前主诉仍未明确，本轮优先单问主诉澄清 gap。");
        }

        List<QuestionGap> routineGaps = sorted.stream()
            .filter(gap -> gap.getSource() == QuestionGapSource.PATTERN && gap.getPriority() != null && gap.getPriority() >= 70)
            .limit(1)
            .toList();
        if (!routineGaps.isEmpty()) {
            return new PolicySelection(routineGaps, "按照场景规则定义的优先级顺序提问。");
        }

        if (!questionPlanSupport.hasAskedAllCoreSlots(context)) {
            List<SlotCode> coreSlots = questionPlanSupport.getCoreRequiredSlots();
            List<QuestionGap> coreGaps = sorted.stream()
                .filter(gap -> coreSlots.contains(gap.getSlot()))
                .sorted(Comparator.comparing(gap -> questionPlanSupport.getReasoningOrder(gap.getSlot())))
                .limit(1)
                .toList();
            if (!coreGaps.isEmpty()) {
                return new PolicySelection(coreGaps, "优先询问核心必问槽位以确保基础信息完整（按临床推理顺序）。");
            }
        }

        return new PolicySelection(sorted.stream().limit(1).toList(), "按最高优先级 gap 继续追问。");
    }

    private Set<SlotCode> unresolvedRiskSlots(TriageContext context) {
        RiskDecision riskDecision = context == null ? null : context.getRiskDecision();
        return riskDecision == null || riskDecision.getUnresolvedRiskGaps() == null
            ? Set.of()
            : riskDecision.getUnresolvedRiskGaps().stream().map(RiskGap::getSlot).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private boolean isRiskGap(QuestionGap gap) {
        return gap != null && gap.getGapType() == QuestionGapType.RISK_REQUIRED;
    }

    private boolean isConflictingGap(TriageContext context, QuestionGap gap) {
        SlotValue slotValue = context == null || context.getSlotState() == null || gap == null ? null : context.getSlotState().get(gap.getSlot());
        return slotValue != null && slotValue.getStatus() == SlotStatus.CONFLICTING;
    }

    private QuestionPlannerResult buildEmergencyFallbackResult(TriageContext context, String triggerReason, QuestionPlannerResult result) {
        int consecutiveLLMFallbackCount = countConsecutiveLLMFallbacks(context) + 1;
        result.setLlmFallbackTriggered(true);

        if (consecutiveLLMFallbackCount >= 3) {
            QuestionPlan forcedReportPlan = QuestionPlan.builder()
                .nextSlotsToAsk(List.of())
                .pendingSlots(List.of())
                .askCount(0)
                .followUpMode(false)
                .priorityReason("连续兜底次数过多（" + consecutiveLLMFallbackCount + "次），强制生成报告避免死循环")
                .policyReason(triggerReason)
                .build();
            result.setQuestionPlan(forcedReportPlan);
            result.setPendingSlots(List.of());
            result.setLlmFallbackHistory(appendFallbackHistory(context, true));
            return result;
        }

        QuestionPlan emergencyPlan = context.getPrefetchedColdStartQuestionPlan();
        if (emergencyPlan == null) {
            emergencyPlan = coldStartSlotSelector.select(context, consecutiveLLMFallbackCount);
        }
        List<SlotCode> emergencySlots = emergencyPlan == null || emergencyPlan.getNextSlotsToAsk() == null
            ? List.of()
            : emergencyPlan.getNextSlotsToAsk();

        if (emergencySlots.isEmpty()) {
            result.setQuestionPlan(emergencyPlan == null
                ? QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .pendingSlots(List.of())
                    .askCount(0)
                    .followUpMode(false)
                    .priorityReason("LLM 兜底未返回可问槽位")
                    .policyReason(triggerReason)
                    .build()
                : emergencyPlan);
            result.setPendingSlots(List.of());
            result.setLlmFallbackHistory(appendFallbackHistory(context, true));
            return result;
        }

        List<SlotCode> lastAskedSlots = context.getLastAskedSlots() == null ? new ArrayList<>() : new ArrayList<>(context.getLastAskedSlots());
        lastAskedSlots.addAll(emergencySlots);
        while (lastAskedSlots.size() > 9) {
            lastAskedSlots.remove(0);
        }

        result.setQuestionPlan(QuestionPlan.builder()
            .nextSlotsToAsk(new ArrayList<>(emergencySlots))
            .pendingSlots(new ArrayList<>(emergencySlots))
            .askCount(emergencySlots.size())
            .followUpMode(true)
            .priorityReason(emergencyPlan == null ? "LLM 冷启动兜底" : emergencyPlan.getPriorityReason())
            .policyReason(triggerReason)
            .build());
        result.setPendingSlots(new ArrayList<>(emergencySlots));
        result.setLastAskedSlots(lastAskedSlots);
        result.setLlmFallbackHistory(appendFallbackHistory(context, true));
        return result;
    }

    private List<Boolean> appendFallbackHistory(TriageContext context, boolean triggered) {
        List<Boolean> history = context.getLlmFallbackHistory() == null ? new ArrayList<>() : new ArrayList<>(context.getLlmFallbackHistory());
        history.add(triggered);
        return history;
    }

    private record PolicySelection(List<QuestionGap> selectedGaps, String policyReason) {}
}
