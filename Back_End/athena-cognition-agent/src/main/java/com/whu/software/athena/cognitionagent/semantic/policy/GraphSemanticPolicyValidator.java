package com.whu.software.athena.cognitionagent.semantic.policy;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.policy.GraphTextPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContext;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GraphSemanticPolicyValidator {

    private final GraphTextPolicyValidator textPolicy = new GraphTextPolicyValidator();

    public PolicyValidationResult validate(GraphSemanticContext context,
                                           GraphSemanticUpdateDraft draft) {
        if (draft == null) return block("draft", "semantic draft is required");
        if (draft.changes == null) return block("draft.changes", "changes are required");
        if (!normalized(draft.topicTitle)
                .equals(normalized(context.scope().proposedTopicTitle))) {
            return block("draft.topicTitle",
                    "node 5 cannot rename the target topic");
        }
        PolicyValidationResult text = textPolicy.validate(
                "draft.stageUnderstanding", draft.stageUnderstanding);
        if (!text.allowed()) return text;
        text = textPolicy.validate("draft.changeSummary", draft.changeSummary);
        if (!text.allowed()) return text;

        Map<String, CanonicalEvidence> evidence = new HashMap<>();
        context.selectedEvidence().forEach(item -> evidence.put(item.evidenceId, item));
        if (draft.stageUnderstandingEvidenceIds == null
                || draft.stageUnderstandingEvidenceIds.isEmpty()
                || !evidence.keySet().containsAll(draft.stageUnderstandingEvidenceIds)) {
            return block("draft.stageUnderstandingEvidenceIds",
                    "stage understanding must cite selected evidence");
        }
        Set<String> mutableIds = new HashSet<>(context.scope().mutableNodeIds);
        Map<String, GraphNode> readableNodes = new HashMap<>();
        context.readableNodes().forEach(node -> readableNodes.put(node.id, node));

        for (int i = 0; i < draft.changes.size(); i++) {
            SemanticChange change = draft.changes.get(i);
            String field = "draft.changes[" + i + "]";
            if (change.nodeType != GraphNodeType.SELF_REPORTED_FACT
                    && change.nodeType != GraphNodeType.PATTERN_HYPOTHESIS
                    && change.nodeType != GraphNodeType.OPEN_QUESTION) {
                return block(field + ".nodeType",
                        "node 5 may only draft fact, hypothesis, or question nodes");
            }
            text = textPolicy.validate(field + ".content", change.content);
            if (!text.allowed()) return text;
            if (change.changeType == SemanticChangeType.REVISE) {
                GraphNode target = readableNodes.get(change.targetNodeId);
                if (target == null || !mutableIds.contains(change.targetNodeId)
                        || target.type != change.nodeType) {
                    return block(field + ".targetNodeId",
                            "REVISE must target a mutable node of the same type");
                }
            } else if (change.changeType == SemanticChangeType.ADD
                    && change.targetNodeId != null) {
                return block(field + ".targetNodeId",
                        "ADD must not provide an existing target node id");
            }
            if (change.changeType != SemanticChangeType.NO_CHANGE
                    && (change.evidenceIds == null || change.evidenceIds.isEmpty())) {
                return block(field + ".evidenceIds",
                        "every semantic change must cite selected evidence");
            }
            for (String evidenceId : change.evidenceIds) {
                if (!evidence.containsKey(evidenceId)) {
                    return block(field + ".evidenceIds",
                            "semantic change cites evidence outside the selected scope");
                }
            }
            if (change.nodeType == GraphNodeType.SELF_REPORTED_FACT
                    && change.evidenceIds.stream().noneMatch(id -> factEligible(evidence.get(id)))) {
                return block(field + ".nodeType",
                        "article relevance alone cannot become a personal body fact");
            }
        }
        return PolicyValidationResult.pass();
    }

    private boolean factEligible(CanonicalEvidence value) {
        return value != null && (value.factLevel == EvidenceFactLevel.SELF_REPORTED
                || value.factLevel == EvidenceFactLevel.OBSERVED);
    }

    private PolicyValidationResult block(String field, String message) {
        return PolicyValidationResult.block(
                AgentErrorCode.POLICY_BLOCKED, field, message);
    }

    private String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
