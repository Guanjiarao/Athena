package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;

/** Structured model-like output used by local tests before a real model exists. */
public record IntentModelSuggestion(
        String provider,
        String modelName,
        String promptVersion,
        ClueIntent suggestedIntent,
        String rationale,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {

    public IntentModelSuggestion(String provider,
                                 String modelName,
                                 String promptVersion,
                                 ClueIntent suggestedIntent,
                                 String rationale) {
        this(provider, modelName, promptVersion, suggestedIntent, rationale,
                null, null, null, null);
    }
}
