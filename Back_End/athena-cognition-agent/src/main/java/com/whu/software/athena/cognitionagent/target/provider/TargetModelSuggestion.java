package com.whu.software.athena.cognitionagent.target.provider;

import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;

public record TargetModelSuggestion(
        String provider,
        String modelName,
        String promptVersion,
        GraphUpdateRoute route,
        String matchedTopicId,
        String suggestedTopicTitle,
        String rationale,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {
}
