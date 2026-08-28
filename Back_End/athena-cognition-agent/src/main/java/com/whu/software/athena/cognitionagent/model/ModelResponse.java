package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelResponse(
        String provider,
        String modelName,
        JsonNode output,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {
}
