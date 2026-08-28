package com.whu.software.athena.cognitionagent.feedbackworkflow.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationRequest;
import com.whu.software.athena.cognitionagent.feedback.contract.FeedbackNormalizationStatus;
import com.whu.software.athena.cognitionagent.feedback.service.ActionFeedbackNormalizationService;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateRequest;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateStatus;
import com.whu.software.athena.cognitionagent.feedbackgraph.service.FeedbackGraphUpdateService;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowRequest;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowResponse;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import org.springframework.stereotype.Service;

@Service
public class ActionFeedbackWorkflow {

    private static final String WORKFLOW_ID = "ACTION_FEEDBACK_WORKFLOW";

    private final ActionFeedbackNormalizationService normalizationService;
    private final FeedbackGraphUpdateService graphUpdateService;
    private final WorkflowTelemetryRecorder telemetry;
    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ActionFeedbackWorkflow(ActionFeedbackNormalizationService normalizationService,
                                  FeedbackGraphUpdateService graphUpdateService,
                                  WorkflowTelemetryRecorder telemetry) {
        this.normalizationService = normalizationService;
        this.graphUpdateService = graphUpdateService;
        this.telemetry = telemetry;
    }

    public ActionFeedbackWorkflowResponse prepare(ActionFeedbackWorkflowRequest request) {
        long startedAt = System.nanoTime();
        ActionFeedbackWorkflowResponse response = new ActionFeedbackWorkflowResponse();
        response.runId = request == null ? null : request.runId;
        WorkflowRunObservation observation = observation(request);
        try {
            AgentError requestError = validateRequest(request);
            if (requestError != null) {
                response.status = ActionFeedbackWorkflowStatus.REJECTED;
                response.error = requestError;
                observation.schemaResult = SchemaResult.FAIL;
                observation.policyResult = PolicyResult.BLOCK;
                observation.steps.add(new WorkflowNodeStep(
                        "WORKFLOW_INPUT", inputSummary(request), "REJECTED"));
                return response;
            }
            observation.schemaResult = SchemaResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "WORKFLOW_INPUT", inputSummary(request), "PASS"));

            ActionFeedbackNormalizationRequest normalizationRequest =
                    new ActionFeedbackNormalizationRequest();
            normalizationRequest.runId = request.runId;
            normalizationRequest.idempotencyKey = request.idempotencyKey + ":node9";
            normalizationRequest.triggerType = request.triggerType;
            normalizationRequest.contextSnapshotId = request.contextSnapshotId;
            normalizationRequest.graph = request.graph;
            normalizationRequest.existingEvidence = request.existingEvidence;
            normalizationRequest.feedback = request.feedback;
            response.normalizationResult = normalizationService.normalize(normalizationRequest);
            step(observation, GraphContract.FEEDBACK_NORMALIZATION_NODE_ID,
                    response.normalizationResult.status);
            if (response.normalizationResult.status == FeedbackNormalizationStatus.NO_CHANGE) {
                response.status = ActionFeedbackWorkflowStatus.NO_CHANGE;
                return response;
            }
            if (response.normalizationResult.status != FeedbackNormalizationStatus.READY) {
                response.status = switch (response.normalizationResult.status) {
                    case BLOCKED -> ActionFeedbackWorkflowStatus.BLOCKED;
                    case REJECTED -> ActionFeedbackWorkflowStatus.REJECTED;
                    default -> ActionFeedbackWorkflowStatus.FAILED;
                };
                response.error = response.normalizationResult.error;
                return response;
            }

            FeedbackGraphUpdateRequest graphRequest = new FeedbackGraphUpdateRequest();
            graphRequest.runId = request.runId;
            graphRequest.idempotencyKey = request.idempotencyKey + ":node10";
            graphRequest.triggerType = request.triggerType;
            graphRequest.contextSnapshotId = request.contextSnapshotId;
            graphRequest.graph = request.graph;
            graphRequest.normalizedFeedback =
                    response.normalizationResult.normalizedFeedback;
            response.graphUpdateResult = graphUpdateService.prepare(graphRequest);
            step(observation, GraphContract.FEEDBACK_GRAPH_NODE_ID,
                    response.graphUpdateResult.status);
            if (response.graphUpdateResult.status
                    != FeedbackGraphUpdateStatus.READY_FOR_CONFIRMATION) {
                response.status = switch (response.graphUpdateResult.status) {
                    case NO_CHANGE -> ActionFeedbackWorkflowStatus.NO_CHANGE;
                    case STALE -> ActionFeedbackWorkflowStatus.STALE;
                    case BLOCKED -> ActionFeedbackWorkflowStatus.BLOCKED;
                    case REJECTED -> ActionFeedbackWorkflowStatus.REJECTED;
                    default -> ActionFeedbackWorkflowStatus.FAILED;
                };
                response.error = response.graphUpdateResult.error;
                return response;
            }
            response.proposal = response.graphUpdateResult.proposal;
            response.graphPreview = response.graphUpdateResult.graphPreview;
            response.status = ActionFeedbackWorkflowStatus.PROPOSAL_READY;
            response.nextNodeId = "HUMAN_CONFIRMATION";
            observation.policyResult = PolicyResult.PASS;
            return response;
        } catch (RuntimeException exception) {
            response.status = ActionFeedbackWorkflowStatus.FAILED;
            response.error = new AgentError(AgentErrorCode.INTERNAL_ERROR,
                    "action feedback workflow failed", true, null, null);
            return response;
        } finally {
            finish(response, observation, startedAt);
        }
    }

    private AgentError validateRequest(ActionFeedbackWorkflowRequest request) {
        if (request == null) {
            return error(AgentErrorCode.INVALID_REQUEST, "request is required", "request");
        }
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.FEEDBACK_WORKFLOW_VERSION.equals(request.workflowVersion)) {
            return error(AgentErrorCode.UNSUPPORTED_VERSION,
                    "unsupported feedback workflow version", "workflowVersion");
        }
        if (blank(request.runId) || blank(request.idempotencyKey)
                || blank(request.contextSnapshotId)) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "runId, idempotencyKey and contextSnapshotId are required", null);
        }
        if (request.triggerType != GraphTriggerType.ACTION_FEEDBACK) {
            return error(AgentErrorCode.INVALID_REQUEST,
                    "feedback workflow requires ACTION_FEEDBACK", "triggerType");
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) {
            return error(AgentErrorCode.INVALID_REQUEST, graphError, "graph");
        }
        if (request.existingEvidence == null) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "existingEvidence is required", "existingEvidence");
        }
        if (request.feedback == null) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "feedback is required", "feedback");
        }
        return null;
    }

    private void finish(ActionFeedbackWorkflowResponse response,
                        WorkflowRunObservation observation,
                        long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        if (response.status == ActionFeedbackWorkflowStatus.BLOCKED
                || response.status == ActionFeedbackWorkflowStatus.REJECTED
                || response.status == ActionFeedbackWorkflowStatus.STALE) {
            observation.policyResult = PolicyResult.BLOCK;
        } else if (observation.policyResult == null && response.status != null) {
            observation.policyResult = PolicyResult.PASS;
        }
        if (response.proposal != null) {
            observation.evidenceIds = response.proposal.evidenceIds;
            observation.operationCount = response.proposal.operations.size();
            observation.baseGraphVersion = response.proposal.baseGraphVersion;
        }
        if (response.graphPreview != null) {
            observation.previewGraphVersion = response.graphPreview.graphVersion;
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.recordWorkflow(GraphContract.FEEDBACK_WORKFLOW_VERSION,
                response.status == null ? "FAILED" : response.status.name(), observation);
    }

    private WorkflowRunObservation observation(ActionFeedbackWorkflowRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
        value.nodeId = WORKFLOW_ID;
        value.nodeVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
        value.modelProvider = "orchestrator";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        value.baseGraphVersion = request == null || request.graph == null
                ? null : request.graph.graphVersion;
        return value;
    }

    private void step(WorkflowRunObservation observation, String nodeId, Object status) {
        observation.steps.add(new WorkflowNodeStep(
                nodeId, "nodeCompleted=true", "status=" + status));
    }

    private String inputSummary(ActionFeedbackWorkflowRequest request) {
        return "triggerType=" + (request == null ? null : request.triggerType)
                + ",feedbackPresent=" + (request != null && request.feedback != null);
    }

    private AgentError error(AgentErrorCode code, String message, String field) {
        return new AgentError(code, message, false, field, null);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
