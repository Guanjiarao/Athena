package com.whu.software.athena.cognitionagent.graph.validation;

import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GraphIntegrityValidator {

    public String validate(PersonalCognitionGraph graph) {
        if (graph == null) return "graph is required";
        if (!GraphContract.GRAPH_SCHEMA_VERSION.equals(graph.graphSchemaVersion)) {
            return "graph.graphSchemaVersion is unsupported";
        }
        if (blank(graph.graphId)) return "graph.graphId is required";
        if (graph.graphVersion < 0) return "graph.graphVersion must not be negative";
        if (graph.nodes == null || graph.edges == null) return "graph nodes and edges are required";
        Set<String> nodeIds = new HashSet<>();
        Set<String> topicIds = new HashSet<>();
        Map<String, GraphNode> nodesById = new HashMap<>();
        Map<String, Integer> pendingActionsByTopic = new HashMap<>();
        for (GraphNode node : graph.nodes) {
            if (node == null || blank(node.id) || node.type == null || node.status == null) {
                return "every graph node requires id, type, and status";
            }
            if (!nodeIds.add(node.id)) return "graph node ids must be unique";
            if (node.version < 1) return "every graph node version must be positive";
            if (node.evidenceIds == null
                    || node.evidenceIds.size() != new HashSet<>(node.evidenceIds).size()) {
                return "graph node evidenceIds must be present and unique";
            }
            nodesById.put(node.id, node);
            if (node.type == GraphNodeType.TOPIC) {
                if (blank(node.title) || !blank(node.topicId)) {
                    return "topic nodes require title and must not contain topicId";
                }
                topicIds.add(node.id);
            } else if (blank(node.topicId)) {
                return "non-topic nodes require topicId";
            }
            if (node.type == GraphNodeType.SOURCE_EVIDENCE
                    && node.evidenceIds.size() != 1) {
                return "source evidence nodes require exactly one evidenceId";
            }
            if (node.type == GraphNodeType.ACTION) {
                Set<GraphActionFeedbackResult> required = Set.of(
                        GraphActionFeedbackResult.OCCURRED,
                        GraphActionFeedbackResult.NOT_OCCURRED,
                        GraphActionFeedbackResult.UNCERTAIN,
                        GraphActionFeedbackResult.SKIPPED);
                if (node.actionType == null || node.actionStatus == null
                        || blank(node.title) || blank(node.content)
                        || node.feedbackOptions == null
                        || node.feedbackOptions.size() != required.size()
                        || !new HashSet<>(node.feedbackOptions).equals(required)) {
                    return "action nodes require type, status, text, and four feedback options";
                }
                if (node.status == GraphNodeStatus.ACTIVE
                        && node.actionStatus == GraphActionStatus.PENDING) {
                    pendingActionsByTopic.merge(node.topicId, 1, Integer::sum);
                }
            } else if (node.actionType != null || node.actionStatus != null
                    || node.dueAt != null
                    || (node.feedbackOptions != null && !node.feedbackOptions.isEmpty())) {
                return "only action nodes may contain action fields";
            }
        }
        if (pendingActionsByTopic.values().stream().anyMatch(count -> count > 1)) {
            return "a topic cannot have multiple active pending actions";
        }
        Set<String> edgeIds = new HashSet<>();
        for (GraphEdge edge : graph.edges) {
            if (edge == null || blank(edge.id) || edge.type == null
                    || blank(edge.fromNodeId) || blank(edge.toNodeId)) {
                return "every graph edge requires id, type, fromNodeId, and toNodeId";
            }
            if (!edgeIds.add(edge.id)) return "graph edge ids must be unique";
            if (edge.version < 1 || edge.evidenceIds == null
                    || edge.evidenceIds.size() != new HashSet<>(edge.evidenceIds).size()) {
                return "graph edge version and evidenceIds are invalid";
            }
            if (!nodeIds.contains(edge.fromNodeId) || !nodeIds.contains(edge.toNodeId)) {
                return "every graph edge must reference existing nodes";
            }
            String directionError = validateDirection(
                    edge, nodesById.get(edge.fromNodeId), nodesById.get(edge.toNodeId));
            if (directionError != null) return directionError;
        }
        for (GraphNode node : graph.nodes) {
            if (node.type != GraphNodeType.TOPIC && !topicIds.contains(node.topicId)) {
                return "non-topic node topicId must reference a topic node";
            }
        }
        return null;
    }

    private String validateDirection(GraphEdge edge, GraphNode from, GraphNode to) {
        return switch (edge.type) {
            case ABOUT -> from.type == GraphNodeType.SOURCE_EVIDENCE
                    && to.type == GraphNodeType.TOPIC
                    && from.topicId.equals(to.id)
                    ? null : "ABOUT must point from source evidence to topic";
            case GROUNDS -> from.type == GraphNodeType.SOURCE_EVIDENCE
                    && semantic(to.type)
                    && from.topicId.equals(to.topicId)
                    ? null : "GROUNDS must point from source evidence to semantic node";
            case NEXT_STEP_FOR -> from.type == GraphNodeType.ACTION
                    && to.type == GraphNodeType.TOPIC
                    && from.topicId.equals(to.id)
                    ? null : "NEXT_STEP_FOR must point from action to topic";
            case FEEDBACK_FOR -> from.type == GraphNodeType.SOURCE_EVIDENCE
                    && to.type == GraphNodeType.ACTION
                    && from.topicId.equals(to.topicId)
                    ? null : "FEEDBACK_FOR must point from feedback evidence to action";
            case SUPPORTS, CHALLENGES -> semantic(from.type) && semantic(to.type)
                    && from.topicId.equals(to.topicId)
                    ? null : "semantic relation edges must stay inside one topic";
        };
    }

    private boolean semantic(GraphNodeType type) {
        return type == GraphNodeType.SELF_REPORTED_FACT
                || type == GraphNodeType.PATTERN_HYPOTHESIS
                || type == GraphNodeType.OPEN_QUESTION;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
