package com.whu.software.athena.cognitionagent.evidence.policy;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;

import java.util.List;

public class EvidenceCanonicalizationPolicyValidator {

    public PolicyValidationResult validate(List<CanonicalEvidence> evidence) {
        for (CanonicalEvidence item : evidence) {
            if (item.sourceType == EvidenceSourceType.ARTICLE_HIGHLIGHT
                    && item.factLevel == EvidenceFactLevel.OBSERVED) {
                return PolicyValidationResult.block(AgentErrorCode.POLICY_BLOCKED,
                        "acceptedEvidence.factLevel",
                        "article content cannot become observed body evidence");
            }
            if (item.sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
                if (item.feedbackResult == null || item.relatedActionId == null
                        || item.relatedActionId.isBlank()
                        || item.factLevel != expectedFeedbackLevel(item.feedbackResult)) {
                    return PolicyValidationResult.block(AgentErrorCode.POLICY_BLOCKED,
                            "acceptedEvidence.feedbackResult",
                            "action feedback metadata and fact level are inconsistent");
                }
                continue;
            }
            if (item.factLevel == EvidenceFactLevel.QUESTION
                    || item.factLevel == EvidenceFactLevel.KNOWLEDGE
                    || item.factLevel == EvidenceFactLevel.PROCESS_EVENT) {
                return PolicyValidationResult.block(AgentErrorCode.POLICY_BLOCKED,
                        "acceptedEvidence.factLevel",
                        "question or knowledge-only evidence cannot enter the body graph");
            }
        }
        return PolicyValidationResult.pass();
    }

    private EvidenceFactLevel expectedFeedbackLevel(GraphActionFeedbackResult result) {
        return switch (result) {
            case OCCURRED, NOT_OCCURRED -> EvidenceFactLevel.OBSERVED;
            case UNCERTAIN -> EvidenceFactLevel.QUESTION;
            case SKIPPED -> EvidenceFactLevel.PROCESS_EVENT;
        };
    }
}
