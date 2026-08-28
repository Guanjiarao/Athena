package com.whu.software.athena.cognitionagent.model;

public record ModelRequest(
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        int maxTokens
) {
}
