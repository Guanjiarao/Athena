package com.whu.software.athena.cognitionagent.semantic.context;

import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;

import java.util.List;

/** Exact model-visible subset for node 5. */
public record GraphSemanticModelContext(
        GraphUpdateRoute route,
        String targetTopicId,
        String targetTopicTitle,
        List<SemanticGraphNodeView> existingNodes,
        List<SemanticEvidenceView> evidences
) {
}
