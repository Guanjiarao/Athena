package com.whu.software.athena.cognitionagent.target.context;

import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;

import java.util.ArrayList;
import java.util.List;

public class GraphTargetContextBuilder {

    public GraphTargetContext build(GraphTargetResolutionRequest request) {
        List<TopicCandidateContext> topics = new ArrayList<>();
        for (GraphNode node : request.graph.nodes) {
            if (node.type == GraphNodeType.TOPIC && node.status == GraphNodeStatus.ACTIVE) {
                topics.add(new TopicCandidateContext(
                        node.id, node.title, node.domain, truncate(node.content, 300)));
            }
        }
        return new GraphTargetContext(request.graph, List.copyOf(request.evidence),
                request.userSelectedTopicId, request.suggestedTopicTitle, List.copyOf(topics));
    }

    public GraphTargetModelContext buildModelContext(GraphTargetContext context) {
        List<TargetEvidenceModelView> evidence = context.evidence().stream()
                .map(item -> new TargetEvidenceModelView(item.evidenceId, item.sourceType,
                        item.factLevel, truncate(item.summary, 500)))
                .toList();
        return new GraphTargetModelContext(
                context.suggestedTopicTitle(), evidence, context.activeTopics());
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
