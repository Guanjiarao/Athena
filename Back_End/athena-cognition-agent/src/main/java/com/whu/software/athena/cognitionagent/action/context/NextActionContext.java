package com.whu.software.athena.cognitionagent.action.context;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;

import java.util.List;

public record NextActionContext(
        NextActionPlanningRequest request,
        List<CanonicalEvidence> selectedEvidence,
        GraphNode existingPendingAction,
        int pendingActionCount
) {
}
