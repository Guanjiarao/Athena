package com.whu.software.athena.cognitionagent.feedback;

import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackSubmission;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowRequest;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdgeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ActionFeedbackTestFixtures {

    public static final String TOPIC_ID = "topic_feedback";
    public static final String ACTION_ID = "action_feedback";

    private ActionFeedbackTestFixtures() {
    }

    public static PersonalCognitionGraph graphWithPendingAction() {
        PersonalCognitionGraph graph = new PersonalCognitionGraph();
        graph.graphId = "graph_feedback";
        graph.graphVersion = 3;
        graph.updatedAt = "2026-08-27T09:00:00+08:00";

        GraphNode topic = new GraphNode();
        topic.id = TOPIC_ID;
        topic.type = GraphNodeType.TOPIC;
        topic.status = GraphNodeStatus.ACTIVE;
        topic.title = "Cycle-related mood changes";
        topic.content = "Continue observing whether the timing repeats.";
        topic.createdAt = "2026-08-20T09:00:00+08:00";
        topic.updatedAt = topic.createdAt;
        graph.nodes.add(topic);

        GraphNode action = new GraphNode();
        action.id = ACTION_ID;
        action.type = GraphNodeType.ACTION;
        action.status = GraphNodeStatus.ACTIVE;
        action.topicId = TOPIC_ID;
        action.title = "Record mood before the next period";
        action.content = "Record whether the same mood change occurs.";
        action.evidenceIds = List.of("evidence_action_origin");
        action.actionType = GraphActionType.RECORD_MOOD;
        action.actionStatus = GraphActionStatus.PENDING;
        action.feedbackOptions = List.of(GraphActionFeedbackResult.OCCURRED,
                GraphActionFeedbackResult.NOT_OCCURRED,
                GraphActionFeedbackResult.UNCERTAIN,
                GraphActionFeedbackResult.SKIPPED);
        action.createdAt = "2026-08-20T09:00:00+08:00";
        action.updatedAt = action.createdAt;
        graph.nodes.add(action);

        GraphEdge edge = new GraphEdge();
        edge.id = "edge_action_feedback_topic";
        edge.type = GraphEdgeType.NEXT_STEP_FOR;
        edge.fromNodeId = ACTION_ID;
        edge.toNodeId = TOPIC_ID;
        edge.evidenceIds = List.of("evidence_action_origin");
        edge.createdAt = action.createdAt;
        edge.updatedAt = action.createdAt;
        graph.edges.add(edge);
        return graph;
    }

    public static ActionFeedbackWorkflowRequest request(
            String suffix,
            GraphActionFeedbackResult result) {
        ActionFeedbackWorkflowRequest value = new ActionFeedbackWorkflowRequest();
        value.runId = "run_feedback_" + suffix;
        value.idempotencyKey = "feedback_" + suffix + ":workflow";
        value.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        value.contextSnapshotId = "ctx_feedback_" + suffix;
        value.graph = graphWithPendingAction();
        value.feedback = submission("feedback_" + suffix, ACTION_ID, result);
        return value;
    }

    public static ActionFeedbackSubmission submission(
            String feedbackId,
            String actionId,
            GraphActionFeedbackResult result) {
        ActionFeedbackSubmission value = new ActionFeedbackSubmission();
        value.feedbackId = feedbackId;
        value.actionId = actionId;
        value.result = result;
        value.note = "A short user note.";
        value.occurredAt = "2026-08-27T12:00:00+08:00";
        return value;
    }

    public static CanonicalEvidence existingFeedback(
            String feedbackId,
            GraphActionFeedbackResult result) {
        CanonicalEvidence value = new CanonicalEvidence();
        value.evidenceId = "evidence_existing_feedback";
        value.sourceType = EvidenceSourceType.ACTION_FEEDBACK;
        value.sourceId = feedbackId;
        value.factLevel = result == GraphActionFeedbackResult.SKIPPED
                ? EvidenceFactLevel.PROCESS_EVENT
                : result == GraphActionFeedbackResult.UNCERTAIN
                ? EvidenceFactLevel.QUESTION : EvidenceFactLevel.OBSERVED;
        value.summary = "Existing feedback.";
        value.contentFingerprint = fingerprint(ACTION_ID + "|" + result
                + "|a short user note.");
        value.relatedActionId = ACTION_ID;
        value.feedbackResult = result;
        return value;
    }

    private static String fingerprint(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
