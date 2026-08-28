package com.whu.software.athena.cognitionagent.semantic.context;

import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;

import java.util.List;

public record SemanticGraphNodeView(
        String nodeId,
        GraphNodeType nodeType,
        String title,
        String content,
        List<String> evidenceIds,
        boolean mutable
) {
}
