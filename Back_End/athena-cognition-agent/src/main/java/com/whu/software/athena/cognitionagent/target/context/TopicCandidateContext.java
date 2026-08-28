package com.whu.software.athena.cognitionagent.target.context;

public record TopicCandidateContext(
        String topicId,
        String title,
        String domain,
        String stageSummary
) {
}
