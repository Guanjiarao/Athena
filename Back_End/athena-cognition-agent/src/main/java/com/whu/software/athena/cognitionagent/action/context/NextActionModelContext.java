package com.whu.software.athena.cognitionagent.action.context;

import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;

import java.util.List;

/** Exact model-visible subset for node 6. */
public record NextActionModelContext(
        GraphUpdateRoute route,
        String topicTitle,
        String stageUnderstanding,
        List<String> openQuestions,
        List<ActionEvidenceView> evidences,
        List<GraphActionType> allowedActionTypes
) {
}
