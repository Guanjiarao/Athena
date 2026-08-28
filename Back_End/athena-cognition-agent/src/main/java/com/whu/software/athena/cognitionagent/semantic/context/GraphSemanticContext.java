package com.whu.software.athena.cognitionagent.semantic.context;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;

import java.util.List;

/** Node-internal allow-list. */
public record GraphSemanticContext(
        PersonalCognitionGraph graph,
        GraphUpdateScope scope,
        List<CanonicalEvidence> selectedEvidence,
        List<GraphNode> readableNodes
) {
}
