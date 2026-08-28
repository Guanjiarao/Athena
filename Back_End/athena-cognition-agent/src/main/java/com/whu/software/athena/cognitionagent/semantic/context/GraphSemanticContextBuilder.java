package com.whu.software.athena.cognitionagent.semantic.context;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphSemanticContextBuilder {

    public GraphSemanticContext build(GraphSemanticUpdateRequest request) {
        Map<String, CanonicalEvidence> evidenceById = new HashMap<>();
        request.evidence.forEach(item -> evidenceById.put(item.evidenceId, item));
        List<CanonicalEvidence> selected = request.scope.selectedEvidenceIds.stream()
                .map(evidenceById::get).filter(java.util.Objects::nonNull).toList();
        Set<String> readableIds = new HashSet<>(request.scope.readableNodeIds);
        List<GraphNode> readable = request.graph.nodes.stream()
                .filter(node -> readableIds.contains(node.id)).toList();
        return new GraphSemanticContext(
                request.graph, request.scope, selected, readable);
    }

    public GraphSemanticModelContext buildModelContext(GraphSemanticContext context) {
        Set<String> mutable = new HashSet<>(context.scope().mutableNodeIds);
        List<SemanticGraphNodeView> nodes = context.readableNodes().stream()
                .map(node -> new SemanticGraphNodeView(
                        node.id, node.type, truncate(node.title, 120),
                        truncate(node.content, 1000),
                        node.evidenceIds == null ? List.of() : List.copyOf(node.evidenceIds),
                        mutable.contains(node.id)))
                .toList();
        List<SemanticEvidenceView> evidence = context.selectedEvidence().stream()
                .map(item -> new SemanticEvidenceView(
                        item.evidenceId, item.sourceType, item.factLevel,
                        truncate(item.summary, 800), item.occurredAt, item.cycleRelation,
                        item.severity, item.resolved))
                .toList();
        return new GraphSemanticModelContext(
                context.scope().route, context.scope().targetTopicId,
                context.scope().proposedTopicTitle, nodes, evidence);
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
