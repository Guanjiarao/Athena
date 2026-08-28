package com.whu.software.athena.cognitionagent.target.context;

import java.util.List;

/** Smaller model-visible allow-list; the complete graph is intentionally absent. */
public record GraphTargetModelContext(
        String suggestedTopicTitle,
        List<TargetEvidenceModelView> evidence,
        List<TopicCandidateContext> candidateTopics
) {
}
