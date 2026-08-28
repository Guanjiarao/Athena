package com.whu.software.athena.cognitionagent.scope.context;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.List;

/** Node-internal allow-list. Node 4 has no model-visible context. */
public record GraphUpdateScopeContext(
        PersonalCognitionGraph graph,
        List<CanonicalEvidence> evidence,
        GraphUpdateRoute targetRoute,
        String targetTopicId,
        String proposedTopicTitle
) {
}
