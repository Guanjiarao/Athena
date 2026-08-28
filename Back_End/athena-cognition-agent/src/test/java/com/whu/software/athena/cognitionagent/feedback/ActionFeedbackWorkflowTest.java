package com.whu.software.athena.cognitionagent.feedback;

import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationRequest;
import com.whu.software.athena.cognitionagent.feedback.service.ActionFeedbackNormalizationService;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateRequest;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateStatus;
import com.whu.software.athena.cognitionagent.feedbackgraph.service.FeedbackGraphUpdateService;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowRequest;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowResponse;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowStatus;
import com.whu.software.athena.cognitionagent.feedbackworkflow.service.ActionFeedbackWorkflow;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdgeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphProposalStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.support.GraphContractCopier;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ActionFeedbackWorkflowTest {

    @Autowired ActionFeedbackWorkflow workflow;
    @Autowired ActionFeedbackNormalizationService normalizationService;
    @Autowired FeedbackGraphUpdateService graphUpdateService;
    @Autowired GraphPatchGuardService guardService;

    @Test
    void occurredFeedbackClosesActionAndBuildsAConfirmableGraphPreview() {
        ActionFeedbackWorkflowRequest request = ActionFeedbackTestFixtures.request(
                "occurred", GraphActionFeedbackResult.OCCURRED);

        ActionFeedbackWorkflowResponse response = workflow.prepare(request);

        assertEquals(ActionFeedbackWorkflowStatus.PROPOSAL_READY, response.status);
        assertEquals("HUMAN_CONFIRMATION", response.nextNodeId);
        assertEquals(3, request.graph.graphVersion, "input graph must stay unchanged");
        assertEquals(GraphActionStatus.PENDING, request.graph.nodes.stream()
                .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                .findFirst().orElseThrow().actionStatus);
        assertEquals(4, response.graphPreview.graphVersion);
        assertEquals(GraphActionStatus.COMPLETED, response.graphPreview.nodes.stream()
                .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                .findFirst().orElseThrow().actionStatus);
        assertTrue(response.graphPreview.nodes.stream().anyMatch(node ->
                node.type == GraphNodeType.SELF_REPORTED_FACT));
        assertTrue(response.graphPreview.edges.stream().anyMatch(edge ->
                edge.type == GraphEdgeType.FEEDBACK_FOR));
        assertEquals("READY_FOR_CONFIRMATION", response.proposal.status.name());
    }

    @Test
    void uncertainFeedbackCreatesQuestionInsteadOfBodyFact() {
        ActionFeedbackWorkflowResponse response = workflow.prepare(
                ActionFeedbackTestFixtures.request(
                        "uncertain", GraphActionFeedbackResult.UNCERTAIN));

        assertEquals(ActionFeedbackWorkflowStatus.PROPOSAL_READY, response.status);
        assertTrue(response.graphPreview.nodes.stream().anyMatch(node ->
                node.type == GraphNodeType.OPEN_QUESTION));
        assertTrue(response.proposal.operations.stream().noneMatch(operation ->
                operation.node != null
                        && operation.node.type == GraphNodeType.SELF_REPORTED_FACT));
    }

    @Test
    void skippedFeedbackClosesActionWithoutInventingBodyMeaning() {
        ActionFeedbackWorkflowResponse response = workflow.prepare(
                ActionFeedbackTestFixtures.request(
                        "skipped", GraphActionFeedbackResult.SKIPPED));

        assertEquals(ActionFeedbackWorkflowStatus.PROPOSAL_READY, response.status);
        assertEquals(GraphActionStatus.SKIPPED, response.graphPreview.nodes.stream()
                .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                .findFirst().orElseThrow().actionStatus);
        assertTrue(response.proposal.operations.stream().noneMatch(operation ->
                operation.node != null
                        && (operation.node.type == GraphNodeType.SELF_REPORTED_FACT
                        || operation.node.type == GraphNodeType.OPEN_QUESTION)));
    }

    @Test
    void duplicateFeedbackIsIdempotentAndDoesNotReachNode10() {
        ActionFeedbackWorkflowRequest request = ActionFeedbackTestFixtures.request(
                "duplicate", GraphActionFeedbackResult.NOT_OCCURRED);
        request.existingEvidence = List.of(ActionFeedbackTestFixtures.existingFeedback(
                request.feedback.feedbackId, request.feedback.result));

        ActionFeedbackWorkflowResponse response = workflow.prepare(request);

        assertEquals(ActionFeedbackWorkflowStatus.NO_CHANGE, response.status);
        assertNotNull(response.normalizationResult);
        assertNull(response.graphUpdateResult);
        assertNull(response.proposal);

        request.feedback.note = "Changed content under the same feedback id.";
        assertEquals(ActionFeedbackWorkflowStatus.BLOCKED,
                workflow.prepare(request).status);
    }

    @Test
    void completedOrUnknownActionCannotReceiveFeedback() {
        ActionFeedbackWorkflowRequest completed = ActionFeedbackTestFixtures.request(
                "completed", GraphActionFeedbackResult.OCCURRED);
        completed.graph.nodes.stream()
                .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                .findFirst().orElseThrow().actionStatus = GraphActionStatus.COMPLETED;
        assertEquals(ActionFeedbackWorkflowStatus.BLOCKED,
                workflow.prepare(completed).status);

        ActionFeedbackWorkflowRequest unknown = ActionFeedbackTestFixtures.request(
                "unknown", GraphActionFeedbackResult.OCCURRED);
        unknown.feedback.actionId = "missing_action";
        assertEquals(ActionFeedbackWorkflowStatus.BLOCKED,
                workflow.prepare(unknown).status);
    }

    @Test
    void node10RejectsStaleOrTamperedNormalizedFeedback() {
        ActionFeedbackWorkflowRequest workflowRequest = ActionFeedbackTestFixtures.request(
                "stale", GraphActionFeedbackResult.OCCURRED);
        ActionFeedbackNormalizationRequest normalizationRequest =
                new ActionFeedbackNormalizationRequest();
        normalizationRequest.runId = workflowRequest.runId;
        normalizationRequest.idempotencyKey = workflowRequest.idempotencyKey + ":node9";
        normalizationRequest.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        normalizationRequest.contextSnapshotId = workflowRequest.contextSnapshotId;
        normalizationRequest.graph = workflowRequest.graph;
        normalizationRequest.feedback = workflowRequest.feedback;
        var normalized = normalizationService.normalize(normalizationRequest)
                .normalizedFeedback;

        FeedbackGraphUpdateRequest graphRequest = new FeedbackGraphUpdateRequest();
        graphRequest.runId = workflowRequest.runId;
        graphRequest.idempotencyKey = workflowRequest.idempotencyKey + ":node10";
        graphRequest.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        graphRequest.contextSnapshotId = workflowRequest.contextSnapshotId;
        graphRequest.graph = workflowRequest.graph;
        graphRequest.normalizedFeedback = normalized;
        graphRequest.graph.graphVersion++;
        assertEquals(FeedbackGraphUpdateStatus.STALE,
                graphUpdateService.prepare(graphRequest).status);

        graphRequest.graph.graphVersion = normalized.baseGraphVersion;
        normalized.topicId = "another_topic";
        assertEquals(FeedbackGraphUpdateStatus.BLOCKED,
                graphUpdateService.prepare(graphRequest).status);
    }

    @Test
    void node10RejectsIncompleteOrSemanticallyInconsistentFeedbackEvidence() {
        ActionFeedbackWorkflowRequest workflowRequest = ActionFeedbackTestFixtures.request(
                "invalid_evidence", GraphActionFeedbackResult.OCCURRED);
        ActionFeedbackNormalizationRequest normalizationRequest =
                new ActionFeedbackNormalizationRequest();
        normalizationRequest.runId = workflowRequest.runId;
        normalizationRequest.idempotencyKey = workflowRequest.idempotencyKey + ":node9";
        normalizationRequest.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        normalizationRequest.contextSnapshotId = workflowRequest.contextSnapshotId;
        normalizationRequest.graph = workflowRequest.graph;
        normalizationRequest.feedback = workflowRequest.feedback;
        var normalized = normalizationService.normalize(normalizationRequest)
                .normalizedFeedback;

        FeedbackGraphUpdateRequest graphRequest = new FeedbackGraphUpdateRequest();
        graphRequest.runId = workflowRequest.runId;
        graphRequest.idempotencyKey = workflowRequest.idempotencyKey + ":node10";
        graphRequest.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        graphRequest.contextSnapshotId = workflowRequest.contextSnapshotId;
        graphRequest.graph = workflowRequest.graph;
        graphRequest.normalizedFeedback = normalized;

        normalized.evidence.evidenceId = null;
        assertEquals(FeedbackGraphUpdateStatus.REJECTED,
                graphUpdateService.prepare(graphRequest).status);

        normalized.evidence.evidenceId = "evidence_restored";
        normalized.evidence.factLevel = EvidenceFactLevel.QUESTION;
        assertEquals(FeedbackGraphUpdateStatus.REJECTED,
                graphUpdateService.prepare(graphRequest).status);
    }

    @Test
    void guardRejectsPartialFeedbackPatchThatDoesNotLinkFeedbackToAction() {
        ActionFeedbackWorkflowRequest request = ActionFeedbackTestFixtures.request(
                "partial", GraphActionFeedbackResult.OCCURRED);
        ActionFeedbackWorkflowResponse prepared = workflow.prepare(request);
        var proposal = new GraphContractCopier().proposal(prepared.proposal);
        proposal.status = GraphProposalStatus.DRAFT;
        proposal.operations.removeIf(operation -> operation.edge != null
                && operation.edge.type == GraphEdgeType.FEEDBACK_FOR);

        GraphUpdateScope scope = new GraphUpdateScope();
        scope.graphId = request.graph.graphId;
        scope.baseGraphVersion = request.graph.graphVersion;
        scope.route = GraphUpdateRoute.UPDATE_EXISTING;
        scope.targetTopicId = ActionFeedbackTestFixtures.TOPIC_ID;
        scope.selectedEvidenceIds = proposal.evidenceIds;
        scope.readableNodeIds = List.of(ActionFeedbackTestFixtures.TOPIC_ID,
                ActionFeedbackTestFixtures.ACTION_ID);
        scope.mutableNodeIds = List.of(ActionFeedbackTestFixtures.TOPIC_ID,
                ActionFeedbackTestFixtures.ACTION_ID);

        GraphPatchGuardRequest guardRequest = new GraphPatchGuardRequest();
        guardRequest.runId = request.runId + "_partial_guard";
        guardRequest.idempotencyKey = request.idempotencyKey + ":partial-guard";
        guardRequest.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        guardRequest.contextSnapshotId = request.contextSnapshotId;
        guardRequest.graph = request.graph;
        guardRequest.evidence = List.of(
                prepared.normalizationResult.normalizedFeedback.evidence);
        guardRequest.scope = scope;
        guardRequest.proposal = proposal;

        assertEquals(PatchGuardStatus.BLOCKED,
                guardService.guard(guardRequest).status);
    }
}
