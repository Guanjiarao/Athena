

package com.nageoffer.ai.ragent.triage.risk;

import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;
import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 风险 Agent。负责 LLM 风险识别、风险追问建议和红旗中断判断。
 */
@Component
@RequiredArgsConstructor
public class RiskAgent {

    private final RiskStratifierWorker riskStratifierWorker;

    public RiskAgentResult assess(TriageContext context, NormalizedTurn normalizedTurn) {
        TriageContext workingContext = cloneRiskWorkingContext(context);
        riskStratifierWorker.execute(workingContext);
        RiskDecision riskDecision = workingContext.getRiskDecision();
        return RiskAgentResult.builder()
                .riskLevel(workingContext.getRiskAssessment())
                .riskDecision(riskDecision)
                .interrupt(riskDecision == null ? Boolean.FALSE : riskDecision.getShouldInterrupt())
                .warningReason(riskDecision == null ? null : riskDecision.getDecisionReason())
                .riskGaps(riskDecision == null || riskDecision.getUnresolvedRiskGaps() == null
                        ? new ArrayList<RiskGap>()
                        : new ArrayList<>(riskDecision.getUnresolvedRiskGaps()))
                .build();
    }

    private TriageContext cloneRiskWorkingContext(TriageContext source) {
        if (source == null) {
            return new TriageContext();
        }
        TriageContext clone = TriageContext.builder()
                .sessionId(source.getSessionId())
                .userInput(source.getUserInput())
                .latestUserTurn(source.getLatestUserTurn())
                .conversationSummary(source.getConversationSummary())
                .finalPrimaryComplaint(source.getFinalPrimaryComplaint())
                .extractedSymptoms(source.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(source.getExtractedSymptoms()))
                .factHistory(source.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getFactHistory()))
                .slotState(source.getSlotState())
                .riskSignalState(source.getRiskSignalState() == null ? new ArrayList<>() : new ArrayList<>(source.getRiskSignalState()))
                .riskDecisionHistory(source.getRiskDecisionHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getRiskDecisionHistory()))
                .build();
        clone.ensureCollections();
        return clone;
    }
}
