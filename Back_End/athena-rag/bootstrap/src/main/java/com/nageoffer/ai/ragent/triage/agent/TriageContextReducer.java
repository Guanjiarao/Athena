

package com.nageoffer.ai.ragent.triage.agent;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.NormalizationAgentResult;
import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;
import com.nageoffer.ai.ragent.triage.question.QuestionPlannerResult;
import com.nageoffer.ai.ragent.triage.rule.RuleAgentResult;
import com.nageoffer.ai.ragent.triage.risk.RiskAgentResult;
import com.nageoffer.ai.ragent.triage.slot.SlotAgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context Reducer 是多 Agent 架构中的单写者。
 *
 * <p>各 Agent 只返回 result，Reducer 负责把 normalization/risk/rule/slot/question plan 的结果
 * 合并写回 TriageContext，避免多个 Agent 并发直接修改同一个上下文。</p>
 */
@Slf4j
@Component
public class TriageContextReducer {

    public void applyNormalization(TriageContext context, NormalizationAgentResult normalizationResult) {
        if (context == null || normalizationResult == null) {
            return;
        }
        context.ensureCollections();
        if (normalizationResult.getLatestTurnUnderstanding() != null) {
            context.setLatestTurnUnderstanding(normalizationResult.getLatestTurnUnderstanding());
        }
        context.setFactHistory(normalizationResult.getFactHistory());
        context.setExtractedSymptoms(normalizationResult.getExtractedSymptoms());
        context.setFinalPrimaryComplaint(normalizationResult.getFinalPrimaryComplaint());
        context.setLatestStateReducerResult(normalizationResult.getLatestStateReducerResult());
        context.setStateReducerHistory(normalizationResult.getStateReducerHistory());
        context.setRiskSignalState(normalizationResult.getRiskSignalState());
        context.setCorrectionHistory(normalizationResult.getCorrectionHistory());
    }

    public void apply(TriageContext context,
                      NormalizedTurn normalizedTurn,
                      RiskAgentResult riskResult,
                      RuleAgentResult ruleResult,
                      SlotAgentResult slotResult,
                      QuestionPlannerResult plannerResult) {
        apply(context, normalizedTurn, riskResult, ruleResult, slotResult);
        applyQuestionPlan(context, plannerResult);
    }

    public void apply(TriageContext context,
                      NormalizedTurn normalizedTurn,
                      RiskAgentResult riskResult,
                      RuleAgentResult ruleResult,
                      SlotAgentResult slotResult) {
        if (context == null) {
            return;
        }
        context.ensureCollections();
        if (slotResult != null && slotResult.getSlotPatch() != null && !slotResult.getSlotPatch().isEmpty()) {
            SlotState slotState = SlotState.empty();
            slotState.setSlots(new LinkedHashMap<>(slotResult.getSlotPatch()));
            context.setSlotState(slotState);
        }
        if (slotResult != null && slotResult.getAnsweredSlots() != null) {
            context.setAnsweredSlots(new ArrayList<>(slotResult.getAnsweredSlots()));
        }
        if (riskResult != null) {
            context.setRiskAssessment(riskResult.getRiskLevel());
            if (riskResult.getRiskDecision() != null) {
                context.appendRiskDecision(riskResult.getRiskDecision());
            }
        }
        List<QuestionGap> reducerMergedGaps = mergeRuleAndRiskGaps(ruleResult, riskResult);
        if (!reducerMergedGaps.isEmpty()) {
            context.setCandidateQuestionGaps(reducerMergedGaps);
        }
        List<TriageClarificationData.QuestionOption> ruleOptions = ruleResult == null ? null : ruleResult.getOptions();
        if (ruleOptions != null && !ruleOptions.isEmpty()) {
            context.setGeneratedOptions(new ArrayList<>(ruleOptions));
        }
    }

    public void applyQuestionPlan(TriageContext context, QuestionPlannerResult plannerResult) {
        if (context == null || plannerResult == null) {
            return;
        }
        context.ensureCollections();
        if (plannerResult.getQuestionPlan() != null) {
            context.setQuestionPlan(plannerResult.getQuestionPlan());
        }
        context.setCandidateQuestionGaps(plannerResult.getCandidateQuestionGaps());
        context.setSelectedQuestionGaps(plannerResult.getSelectedQuestionGaps());
        context.setSuppressedQuestionGaps(plannerResult.getSuppressedQuestionGaps());
        context.setAskabilityDecisions(plannerResult.getAskabilityDecisions());
        context.setPendingSlots(plannerResult.getPendingSlots());
        context.setLastAskedSlots(plannerResult.getLastAskedSlots());
        context.setLlmFallbackHistory(plannerResult.getLlmFallbackHistory());
        context.setForceGenerateReport(plannerResult.isForceGenerateReport());
        context.setForceGenerateReportReason(plannerResult.getForceGenerateReportReason());
    }

    private List<QuestionGap> mergeRuleAndRiskGaps(RuleAgentResult ruleResult,
                                                   RiskAgentResult riskResult) {
        Map<String, QuestionGap> deduplicated = new LinkedHashMap<>();
        addGaps(deduplicated, ruleResult == null ? null : ruleResult.getRuleGaps());
        addGaps(deduplicated, toQuestionGaps(riskResult == null ? null : riskResult.getRiskGaps()));
        return new ArrayList<>(deduplicated.values());
    }

    private List<QuestionGap> toQuestionGaps(List<RiskGap> riskGaps) {
        if (riskGaps == null || riskGaps.isEmpty()) {
            return List.of();
        }
        List<QuestionGap> questionGaps = new ArrayList<>();
        for (RiskGap riskGap : riskGaps) {
            if (riskGap == null || riskGap.getSlot() == null) {
                continue;
            }
            questionGaps.add(QuestionGap.builder()
                .slot(riskGap.getSlot())
                .gapType(QuestionGapType.RISK_REQUIRED)
                .source(QuestionGapSource.RISK_POLICY)
                .priority(riskGap.getPriority() == null ? 90 : riskGap.getPriority())
                .reason(riskGap.getReason())
                .build());
        }
        return questionGaps;
    }

    private void addGaps(Map<String, QuestionGap> deduplicated, List<QuestionGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return;
        }
        for (QuestionGap gap : gaps) {
            if (gap == null || gap.getSlot() == null) {
                continue;
            }
            String key = gap.getSlot().name();
            QuestionGap existing = deduplicated.get(key);
            if (existing == null || safePriority(gap) > safePriority(existing)) {
                deduplicated.put(key, gap);
            }
        }
    }

    private int safePriority(QuestionGap gap) {
        return gap == null || gap.getPriority() == null ? 0 : gap.getPriority();
    }
}
