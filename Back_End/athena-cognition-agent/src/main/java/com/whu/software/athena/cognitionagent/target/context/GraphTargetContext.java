package com.whu.software.athena.cognitionagent.target.context;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.List;

/** Full node-internal allow-list. */
public record GraphTargetContext(
        PersonalCognitionGraph graph,
        List<CanonicalEvidence> evidence,
        String userSelectedTopicId,
        String suggestedTopicTitle,
        List<TopicCandidateContext> activeTopics
) {
}
