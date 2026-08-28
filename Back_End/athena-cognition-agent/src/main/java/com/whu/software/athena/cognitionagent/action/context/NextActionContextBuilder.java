package com.whu.software.athena.cognitionagent.action.context;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NextActionContextBuilder {

    private static final List<GraphActionType> ALLOWED_TYPES = List.of(
            GraphActionType.RECORD_BODY,
            GraphActionType.RECORD_MOOD,
            GraphActionType.RECORD_SLEEP,
            GraphActionType.ANSWER_QUESTION,
            GraphActionType.CONFIRM_STATUS);

    public NextActionContext build(NextActionPlanningRequest request) {
        Map<String, CanonicalEvidence> byId = new HashMap<>();
        request.evidence.forEach(item -> byId.put(item.evidenceId, item));
        List<CanonicalEvidence> selected = request.scope.selectedEvidenceIds.stream()
                .map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<GraphNode> pending = request.graph.nodes.stream()
                .filter(node -> node.type == GraphNodeType.ACTION
                        && node.status == GraphNodeStatus.ACTIVE
                        && node.actionStatus == GraphActionStatus.PENDING
                        && request.scope.targetTopicId != null
                        && request.scope.targetTopicId.equals(node.topicId))
                .toList();
        GraphNode existing = pending.isEmpty() ? null : pending.get(0);
        return new NextActionContext(request, selected, existing, pending.size());
    }

    public NextActionModelContext buildModelContext(NextActionContext context) {
        List<String> questions = context.request().semanticDraft.changes.stream()
                .filter(change -> change.changeType != SemanticChangeType.NO_CHANGE
                        && change.nodeType == GraphNodeType.OPEN_QUESTION)
                .map(change -> truncate(change.content, 500)).toList();
        List<ActionEvidenceView> evidence = context.selectedEvidence().stream()
                .map(item -> new ActionEvidenceView(item.evidenceId, item.sourceType,
                        item.factLevel, truncate(item.summary, 500)))
                .toList();
        return new NextActionModelContext(
                context.request().scope.route,
                truncate(context.request().scope.proposedTopicTitle, 80),
                truncate(context.request().semanticDraft.stageUnderstanding, 800),
                questions, evidence, ALLOWED_TYPES);
    }

    public List<GraphActionType> allowedTypes() {
        return ALLOWED_TYPES;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
