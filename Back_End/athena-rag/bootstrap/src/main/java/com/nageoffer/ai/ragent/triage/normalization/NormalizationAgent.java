

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Normalization Agent 是多 Agent 架构的入口语义层。
 *
 * <p>对外保持 result-only：输入当前轮上下文快照，输出 NormalizationAgentResult/NormalizedTurn，
 * 不决定下一问、不做风险中断、不查规则、不组装响应。当前内部复用旧 normalization worker，
 * 并限制其只作用在 workingContext 上；状态写回统一由 TriageContextReducer 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class NormalizationAgent {

    private final TurnUnderstandingWorker turnUnderstandingWorker;
    private final SemanticParserWorker semanticParserWorker;
    private final FactExtractor factExtractor;

    public NormalizedTurn normalize(TriageContext context) {
        return normalizeToResult(context).getNormalizedTurn();
    }

    @RagTraceNode(name = "NormalizationAgent", type = "TRIAGE_NORM")
    public NormalizationAgentResult normalizeToResult(TriageContext context) {
        TurnInputSnapshot snapshot = TurnInputSnapshot.from(context);
        return normalizeToResult(context, snapshot);
    }

    public NormalizationAgentResult normalizeToResult(TriageContext context, TurnInputSnapshot snapshot) {
        if (context == null) {
            NormalizedTurn emptyTurn = NormalizedTurn.builder().build();
            return NormalizationAgentResult.builder().normalizedTurn(emptyTurn).build();
        }
        context.ensureCollections();
        TriageContext workingContext = cloneNormalizationWorkingContext(context);
        turnUnderstandingWorker.execute(workingContext);
        semanticParserWorker.execute(workingContext);
        factExtractor.execute(workingContext);
        NormalizedTurn normalizedTurn = NormalizedTurn.builder()
                .turnUnderstanding(workingContext.getLatestTurnUnderstanding())
                .primaryComplaint(workingContext.getFinalPrimaryComplaint())
                .signals(collectSignals(workingContext, snapshot))
                .symptoms(workingContext.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getExtractedSymptoms()))
                .facts(workingContext.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getFactHistory()))
                .answeredSlots(collectAnsweredSlots(workingContext))
                .build();
        return NormalizationAgentResult.builder()
                .normalizedTurn(normalizedTurn)
                .latestTurnUnderstanding(workingContext.getLatestTurnUnderstanding())
                .factHistory(workingContext.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getFactHistory()))
                .extractedSymptoms(workingContext.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getExtractedSymptoms()))
                .finalPrimaryComplaint(workingContext.getFinalPrimaryComplaint())
                .latestStateReducerResult(workingContext.getLatestStateReducerResult())
                .stateReducerHistory(workingContext.getStateReducerHistory() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getStateReducerHistory()))
                .riskSignalState(workingContext.getRiskSignalState() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getRiskSignalState()))
                .correctionHistory(workingContext.getCorrectionHistory() == null ? new ArrayList<>() : new ArrayList<>(workingContext.getCorrectionHistory()))
                .build();
    }

    private TriageContext cloneNormalizationWorkingContext(TriageContext source) {
        TriageContext clone = TriageContext.builder()
                .sessionId(source.getSessionId())
                .userInput(source.getUserInput())
                .latestUserTurn(source.getLatestUserTurn())
                .conversationSummary(source.getConversationSummary())
                .finalPrimaryComplaint(source.getFinalPrimaryComplaint())
                .conversationHistory(source.getConversationHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getConversationHistory()))
                .factHistory(source.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getFactHistory()))
                .extractedSymptoms(source.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(source.getExtractedSymptoms()))
                .latestTurnUnderstanding(source.getLatestTurnUnderstanding())
                .turnUnderstandingHistory(source.getTurnUnderstandingHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getTurnUnderstandingHistory()))
                .latestStateReducerResult(source.getLatestStateReducerResult())
                .stateReducerHistory(source.getStateReducerHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getStateReducerHistory()))
                .riskSignalState(source.getRiskSignalState() == null ? new ArrayList<>() : new ArrayList<>(source.getRiskSignalState()))
                .correctionHistory(source.getCorrectionHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getCorrectionHistory()))
                .slotState(source.getSlotState())
                .answeredSlots(source.getAnsweredSlots() == null ? new ArrayList<>() : new ArrayList<>(source.getAnsweredSlots()))
                .pendingSlots(source.getPendingSlots() == null ? new ArrayList<>() : new ArrayList<>(source.getPendingSlots()))
                .lastAskedSlots(source.getLastAskedSlots() == null ? new ArrayList<>() : new ArrayList<>(source.getLastAskedSlots()))
                .build();
        clone.ensureCollections();
        return clone;
    }

    private List<String> collectSignals(TriageContext context, TurnInputSnapshot snapshot) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        if (context.getFinalPrimaryComplaint() != null && !context.getFinalPrimaryComplaint().isBlank()) {
            signals.add(context.getFinalPrimaryComplaint().trim());
        }
        if (context.getExtractedSymptoms() != null) {
            context.getExtractedSymptoms().forEach(symptom -> {
                if (symptom != null && symptom.getName() != null && !symptom.getName().isBlank()) {
                    signals.add(symptom.getName().trim());
                }
            });
        }
        if (signals.isEmpty() && snapshot != null
                && snapshot.getLatestUserInput() != null && !snapshot.getLatestUserInput().isBlank()) {
            signals.add(snapshot.getLatestUserInput().trim());
        }
        return new ArrayList<>(signals);
    }

    private List<SlotCode> collectAnsweredSlots(TriageContext context) {
        if (context == null) {
            return List.of();
        }
        LinkedHashSet<SlotCode> slots = new LinkedHashSet<>();
        if (context.getLatestTurnUnderstanding() != null
                && context.getLatestTurnUnderstanding().getAnsweredSlots() != null) {
            context.getLatestTurnUnderstanding().getAnsweredSlots().forEach(answered -> {
                if (answered != null && answered.getSlot() != null) {
                    slots.add(answered.getSlot());
                }
            });
        }
        List<Fact> facts = context.getFactHistory();
        if (facts != null) {
            for (Fact fact : facts) {
                if (fact != null && fact.getSlot() != null) {
                    slots.add(fact.getSlot());
                }
            }
        }
        return new ArrayList<>(slots);
    }
}
