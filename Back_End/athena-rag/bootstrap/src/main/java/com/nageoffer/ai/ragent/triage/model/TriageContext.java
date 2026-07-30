

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.engine.TriageState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared context for the whole triage session lifecycle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageContext {

    private String sessionId;
    private String userInput;
    private String latestUserTurn;
    private String conversationSummary;
    private String finalPrimaryComplaint;

    @Default
    private Integer clarificationTurnCount = 0;

    @Default
    private Integer totalTurnCount = 0;

    @Default
    private List<String> conversationHistory = new ArrayList<>();

    /**
     * 系统回复历史（用于检测连续通用问题）
     */
    @Default
    private List<String> systemReplyHistory = new ArrayList<>();

    /**
     * LLM 兜底历史（用于检测连续 LLM 兜底）
     */
    @Default
    private List<Boolean> llmFallbackHistory = new ArrayList<>();

    @Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Default
    private List<String> missingFields = new ArrayList<>();

    @Default
    private List<Fact> factHistory = new ArrayList<>();

    @Default
    private SlotState slotState = SlotState.empty();

    @Default
    private List<SlotCode> lastAskedSlots = new ArrayList<>();

    @Default
    private List<SlotCode> answeredSlots = new ArrayList<>();

    @Default
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Default
    private List<QuestionGap> candidateQuestionGaps = new ArrayList<>();

    @Default
    private List<QuestionGap> selectedQuestionGaps = new ArrayList<>();

    @Default
    private List<QuestionGap> suppressedQuestionGaps = new ArrayList<>();

    @Default
    private List<AskabilityDecision> askabilityDecisions = new ArrayList<>();

    private TurnUnderstanding latestTurnUnderstanding;

    @Default
    private List<TurnUnderstanding> turnUnderstandingHistory = new ArrayList<>();

    private StateReducerResult latestStateReducerResult;

    @Default
    private List<StateReducerResult> stateReducerHistory = new ArrayList<>();

    @Default
    private List<RiskSignalUnderstanding> riskSignalState = new ArrayList<>();

    @Default
    private List<CorrectionUnderstanding> correctionHistory = new ArrayList<>();

    private QuestionPlan questionPlan;

    /**
     * Rule Agent miss 后由 Supervisor 预取的 ColdStart LLM 问题计划。
     * QuestionPlanner 只有在真正进入 LLM 兜底时才消费它，避免重复等待同一个冷启动选择。
     */
    private QuestionPlan prefetchedColdStartQuestionPlan;

    private RiskLevel riskAssessment;
    private RiskDecision riskDecision;

    @Default
    private List<RiskDecision> riskDecisionHistory = new ArrayList<>();

    private TriageAction nextAction;
    private String finalReply;
    private TriageState currentState;

    @Default
    private List<String> stateLog = new ArrayList<>();

    @Default
    private List<AuditLog> auditTrail = new ArrayList<>();

    // 临时字段：存储当前轮次生成的选项（用于响应构建）
    @Default
    private List<TriageClarificationData.QuestionOption> generatedOptions = new ArrayList<>();

    // 已激活的语义信号：一旦识别到某个语义信号（如"腹泻"），记录下来，确保对应的 ROUTINE_RULES 在整个对话过程中持续生效
    @Default
    private Set<String> activatedSemanticSignals = new HashSet<>();

    /**
     * 是否强制生成报告（LLM智能兜底决策）
     */
    private Boolean forceGenerateReport;

    /**
     * 强制生成报告的原因
     */
    private String forceGenerateReportReason;

    public void ensureCollections() {
        if (conversationHistory == null) {
            conversationHistory = new ArrayList<>();
        }
        if (systemReplyHistory == null) {
            systemReplyHistory = new ArrayList<>();
        }
        if (llmFallbackHistory == null) {
            llmFallbackHistory = new ArrayList<>();
        }
        if (extractedSymptoms == null) {
            extractedSymptoms = new ArrayList<>();
        }
        if (missingFields == null) {
            missingFields = new ArrayList<>();
        }
        if (factHistory == null) {
            factHistory = new ArrayList<>();
        }
        if (slotState == null) {
            slotState = SlotState.empty();
        }
        if (lastAskedSlots == null) {
            lastAskedSlots = new ArrayList<>();
        }
        if (answeredSlots == null) {
            answeredSlots = new ArrayList<>();
        }
        if (pendingSlots == null) {
            pendingSlots = new ArrayList<>();
        }
        if (candidateQuestionGaps == null) {
            candidateQuestionGaps = new ArrayList<>();
        }
        if (selectedQuestionGaps == null) {
            selectedQuestionGaps = new ArrayList<>();
        }
        if (suppressedQuestionGaps == null) {
            suppressedQuestionGaps = new ArrayList<>();
        }
        if (askabilityDecisions == null) {
            askabilityDecisions = new ArrayList<>();
        }
        if (turnUnderstandingHistory == null) {
            turnUnderstandingHistory = new ArrayList<>();
        }
        if (stateReducerHistory == null) {
            stateReducerHistory = new ArrayList<>();
        }
        if (riskSignalState == null) {
            riskSignalState = new ArrayList<>();
        }
        if (riskDecisionHistory == null) {
            riskDecisionHistory = new ArrayList<>();
        }
        if (correctionHistory == null) {
            correctionHistory = new ArrayList<>();
        }
        if (stateLog == null) {
            stateLog = new ArrayList<>();
        }
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
        if (activatedSemanticSignals == null) {
            activatedSemanticSignals = new HashSet<>();
        }
    }

    public boolean hasMissingFields() {
        return (pendingSlots != null && !pendingSlots.isEmpty())
                || (missingFields != null && !missingFields.isEmpty());
    }

    public void appendConversation(String turnText) {
        ensureCollections();
        if (turnText == null || turnText.isBlank()) {
            return;
        }
        conversationHistory.add(turnText.trim());
    }

    public void appendFacts(List<Fact> facts) {
        ensureCollections();
        if (facts == null || facts.isEmpty()) {
            return;
        }
        factHistory.addAll(facts.stream().filter(each -> each != null).toList());
    }

    public void appendTurnUnderstanding(TurnUnderstanding turnUnderstanding) {
        ensureCollections();
        if (turnUnderstanding == null) {
            return;
        }
        latestTurnUnderstanding = turnUnderstanding;
        turnUnderstandingHistory.add(turnUnderstanding);
    }

    public void appendStateReducerResult(StateReducerResult reducerResult) {
        ensureCollections();
        if (reducerResult == null) {
            return;
        }
        latestStateReducerResult = reducerResult;
        stateReducerHistory.add(reducerResult);
        finalPrimaryComplaint = reducerResult.getComplaintTruth() == null
                ? null
                : reducerResult.getComplaintTruth().getValue();
        if (reducerResult.getAccumulatedRiskSignals() != null) {
            riskSignalState = new ArrayList<>(reducerResult.getAccumulatedRiskSignals());
        }
        if (reducerResult.getCorrectionLog() != null) {
            correctionHistory = new ArrayList<>(reducerResult.getCorrectionLog());
        }
        // 同步 answeredSlots 到 context（关键修复）
        if (reducerResult.getAnsweredSlots() != null) {
            answeredSlots = new ArrayList<>(reducerResult.getAnsweredSlots());
        }
    }

    public void appendRiskDecision(RiskDecision decision) {
        ensureCollections();
        if (decision == null) {
            return;
        }
        riskDecision = decision;
        riskDecisionHistory.add(decision);
    }

    public void resetTurnState() {
        latestTurnUnderstanding = null;
        questionPlan = null;
        riskAssessment = null;
        riskDecision = null;
        nextAction = null;
        finalReply = null;
        extractedSymptoms = new ArrayList<>();
        missingFields = new ArrayList<>();
        // lastAskedSlots 和 answeredSlots 是跨轮次状态，不应该在这里清空
        pendingSlots = new ArrayList<>();
        candidateQuestionGaps = new ArrayList<>();
        selectedQuestionGaps = new ArrayList<>();
        suppressedQuestionGaps = new ArrayList<>();
        askabilityDecisions = new ArrayList<>();
        generatedOptions = new ArrayList<>();  // 清空上一轮的选项
    }

    public List<String> evictOldestTurnsByCharBudget(int targetRecentWindowChars) {
        ensureCollections();
        List<String> evicted = new ArrayList<>();
        while (conversationHistory.size() > 1 && recentConversationChars() > targetRecentWindowChars) {
            evicted.add(conversationHistory.remove(0));
        }
        return evicted;
    }

    public int recentConversationChars() {
        ensureCollections();
        if (conversationHistory.isEmpty()) {
            return 0;
        }
        return String.join("\n", conversationHistory).length();
    }

    public int totalTranscriptChars(boolean includeSummary) {
        return buildConversationTranscript(includeSummary).length();
    }

    public void appendState(String state) {
        ensureCollections();
        if (state == null || state.isBlank()) {
            return;
        }
        stateLog.add(state.trim());
    }

    public void appendAudit(AuditLog auditLog) {
        ensureCollections();
        if (auditLog == null) {
            return;
        }
        auditTrail.add(auditLog);
    }

    public String buildConversationTranscript(boolean includeSummary) {
        ensureCollections();
        StringBuilder builder = new StringBuilder();
        if (includeSummary && conversationSummary != null && !conversationSummary.isBlank()) {
            builder.append("[summary]\n").append(conversationSummary.trim()).append("\n\n");
        }
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            builder.append(String.join("\n", conversationHistory));
        }
        return builder.toString().trim();
    }

    /**
     * 获取已填充的槽位集合（用于重复询问保护）
     * 包括 FILLED、NEGATED、CORRECTED、INFERRED 状态的槽位
     */
    public Set<SlotCode> getFilledSlots() {
        Set<SlotCode> filledSlots = new HashSet<>();
        if (slotState == null || slotState.getSlots() == null) {
            return filledSlots;
        }

        for (SlotValue slotValue : slotState.getSlots().values()) {
            if (slotValue != null && slotValue.getSlot() != null) {
                SlotStatus status = slotValue.getStatus();
                // 认为已填充的状态：FILLED（已填充）、NEGATED（已否定）、CORRECTED（已纠正）、INFERRED（已推断）
                if (status == SlotStatus.FILLED
                    || status == SlotStatus.NEGATED
                    || status == SlotStatus.CORRECTED
                    || status == SlotStatus.INFERRED) {
                    filledSlots.add(slotValue.getSlot());
                }
            }
        }

        return filledSlots;
    }
}
