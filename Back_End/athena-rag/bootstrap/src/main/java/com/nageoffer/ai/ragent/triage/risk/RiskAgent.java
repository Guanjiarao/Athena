

package com.nageoffer.ai.ragent.triage.risk;

import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;
import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Risk Agent 是多 Agent 架构中的安全官。
 *
 * <p>对外保持 result-only：输入当前 context 快照与 NormalizedTurn，输出 RiskAgentResult，
 * 不直接写原始 TriageContext。当前内部复用 RiskStratifierWorker，并限制其只作用在 workingContext 上；
 * 状态写回统一由 TriageContextReducer 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class RiskAgent {

    private final RiskStratifierWorker riskStratifierWorker;

    @RagTraceNode(name = "RiskAgent", type = "TRIAGE_RISK")
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
