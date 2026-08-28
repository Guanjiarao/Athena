package com.whu.software.athena.cognitionagent.action.provider;

public record NextActionModelSuggestion(
        String provider,
        String modelName,
        String promptVersion,
        NextActionModelOutput output,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {
}
