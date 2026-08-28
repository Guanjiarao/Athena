package com.whu.software.athena.cognitionagent.scope.context;

import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;

import java.util.List;

public class GraphUpdateScopeContextBuilder {

    public GraphUpdateScopeContext build(GraphUpdateScopeRequest request) {
        return new GraphUpdateScopeContext(
                request.graph,
                request.evidence == null ? List.of() : List.copyOf(request.evidence),
                request.targetRoute,
                request.targetTopicId,
                request.proposedTopicTitle);
    }
}
