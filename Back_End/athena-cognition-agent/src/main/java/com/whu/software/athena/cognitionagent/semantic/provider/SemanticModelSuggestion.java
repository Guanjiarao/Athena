package com.whu.software.athena.cognitionagent.semantic.provider;

import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;

public record SemanticModelSuggestion(
        String provider,
        String modelName,
        String promptVersion,
        GraphSemanticUpdateDraft draft,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {
}
