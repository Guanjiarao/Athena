package com.whu.software.athena.cognitionagent.feedback.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationRequest;
import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationResponse;
import com.whu.software.athena.cognitionagent.feedback.contract.FeedbackNormalizationStatus;
import com.whu.software.athena.cognitionagent.feedback.contract.NormalizedActionFeedback;
import com.whu.software.athena.cognitionagent.feedback.validation.ActionFeedbackNormalizationRequestValidator;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.patch.service.DeterministicGraphIdFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

@Service
public class ActionFeedbackNormalizationService {

    private final ActionFeedbackNormalizationRequestValidator validator =
            new ActionFeedbackNormalizationRequestValidator();
    private final DeterministicGraphIdFactory ids = new DeterministicGraphIdFactory();
    private final WorkflowTelemetryRecorder telemetry;

    public ActionFeedbackNormalizationService(WorkflowTelemetryRecorder telemetry) {
        this.telemetry = telemetry;
    }

    public ActionFeedbackNormalizationResponse normalize(
            ActionFeedbackNormalizationRequest request) {
        long startedAt = System.nanoTime();
        ActionFeedbackNormalizationResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = FeedbackNormalizationStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(issue.code(), issue.message(), false,
                    issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));

        CanonicalEvidence existing = request.existingEvidence.stream()
                .filter(item -> item != null
                        && item.sourceType == EvidenceSourceType.ACTION_FEEDBACK
                        && request.feedback.feedbackId.equals(item.sourceId))
                .findFirst().orElse(null);
        if (existing != null) {
            String incomingFingerprint = fingerprint(request.feedback.actionId + "|"
                    + request.feedback.result + "|" + normalized(request.feedback.note));
            if (request.feedback.actionId.equals(existing.relatedActionId)
                    && request.feedback.result == existing.feedbackResult
                    && Objects.equals(incomingFingerprint, existing.contentFingerprint)) {
                response.status = FeedbackNormalizationStatus.NO_CHANGE;
                response.policyResult = PolicyResult.PASS;
                observation.evidenceIds = java.util.List.of(existing.evidenceId);
                observation.steps.add(new WorkflowNodeStep(
                        "IDEMPOTENCY_GATE", "feedbackIdPresent=true", "NO_CHANGE"));
            } else {
                response.status = FeedbackNormalizationStatus.BLOCKED;
                response.policyResult = PolicyResult.BLOCK;
                response.error = new AgentError(AgentErrorCode.POLICY_BLOCKED,
                        "feedbackId already belongs to different feedback content",
                        false, "feedback.feedbackId", null);
            }
            return finish(response, observation, startedAt);
        }

        GraphNode action = request.graph.nodes.stream()
                .filter(node -> request.feedback.actionId.equals(node.id))
                .findFirst().orElse(null);
        if (action == null || action.type != GraphNodeType.ACTION
                || action.status != GraphNodeStatus.ACTIVE) {
            return block(response, observation, startedAt,
                    "feedback.actionId", "feedback must target an active action");
        }
        if (action.actionStatus != GraphActionStatus.PENDING) {
            return block(response, observation, startedAt,
                    "feedback.actionId", "only a pending action may receive feedback");
        }
        if (action.feedbackOptions == null
                || !action.feedbackOptions.contains(request.feedback.result)) {
            return block(response, observation, startedAt,
                    "feedback.result", "feedback result is not allowed by the action");
        }

        NormalizedActionFeedback normalized = new NormalizedActionFeedback();
        normalized.feedbackId = request.feedback.feedbackId;
        normalized.actionId = action.id;
        normalized.topicId = action.topicId;
        normalized.result = request.feedback.result;
        normalized.resultingActionStatus = request.feedback.result
                == GraphActionFeedbackResult.SKIPPED
                ? GraphActionStatus.SKIPPED : GraphActionStatus.COMPLETED;
        normalized.baseGraphVersion = request.graph.graphVersion;
        normalized.evidence = evidence(request, action);
        response.normalizedFeedback = normalized;
        response.status = FeedbackNormalizationStatus.READY;
        response.policyResult = PolicyResult.PASS;
        observation.evidenceIds = java.util.List.of(normalized.evidence.evidenceId);
        observation.steps.add(new WorkflowNodeStep(
                "FEEDBACK_NORMALIZATION",
                "actionStatus=PENDING,result=" + request.feedback.result,
                "resultingActionStatus=" + normalized.resultingActionStatus));
        return finish(response, observation, startedAt);
    }

    private CanonicalEvidence evidence(ActionFeedbackNormalizationRequest request,
                                       GraphNode action) {
        CanonicalEvidence value = new CanonicalEvidence();
        value.evidenceId = ids.id("evidence", request.idempotencyKey,
                request.feedback.feedbackId);
        value.sourceType = EvidenceSourceType.ACTION_FEEDBACK;
        value.sourceId = request.feedback.feedbackId;
        value.factLevel = factLevel(request.feedback.result);
        value.summary = summary(action, request.feedback.result, request.feedback.note);
        value.contentFingerprint = fingerprint(action.id + "|" + request.feedback.result
                + "|" + normalized(request.feedback.note));
        value.occurredAt = request.feedback.occurredAt;
        value.relatedActionId = action.id;
        value.feedbackResult = request.feedback.result;
        return value;
    }

    private EvidenceFactLevel factLevel(GraphActionFeedbackResult result) {
        return switch (result) {
            case OCCURRED, NOT_OCCURRED -> EvidenceFactLevel.OBSERVED;
            case UNCERTAIN -> EvidenceFactLevel.QUESTION;
            case SKIPPED -> EvidenceFactLevel.PROCESS_EVENT;
        };
    }

    private String summary(GraphNode action,
                           GraphActionFeedbackResult result,
                           String note) {
        String base = "Feedback " + result + " for action: " + action.title + ".";
        return note == null || note.isBlank() ? base : base + " Note: " + note.trim();
    }

    private String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String fingerprint(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ActionFeedbackNormalizationResponse block(
            ActionFeedbackNormalizationResponse response,
            WorkflowRunObservation observation,
            long startedAt,
            String field,
            String message) {
        response.status = FeedbackNormalizationStatus.BLOCKED;
        response.policyResult = PolicyResult.BLOCK;
        response.error = new AgentError(AgentErrorCode.POLICY_BLOCKED,
                message, false, field, null);
        observation.steps.add(new WorkflowNodeStep(
                "FEEDBACK_POLICY", field, "BLOCK"));
        return finish(response, observation, startedAt);
    }

    private ActionFeedbackNormalizationResponse baseResponse(
            ActionFeedbackNormalizationRequest request) {
        ActionFeedbackNormalizationResponse response =
                new ActionFeedbackNormalizationResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(ActionFeedbackNormalizationRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
        value.nodeId = GraphContract.FEEDBACK_NORMALIZATION_NODE_ID;
        value.nodeVersion = GraphContract.FEEDBACK_NORMALIZATION_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        value.feedbackResult = request == null || request.feedback == null
                || request.feedback.result == null ? null : request.feedback.result.name();
        value.baseGraphVersion = request == null || request.graph == null
                ? null : request.graph.graphVersion;
        return value;
    }

    private ActionFeedbackNormalizationResponse finish(
            ActionFeedbackNormalizationResponse response,
            WorkflowRunObservation observation,
            long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(),
                observation);
        return response;
    }

    private String inputSummary(ActionFeedbackNormalizationRequest request) {
        return "graphVersion=" + (request == null || request.graph == null
                ? null : request.graph.graphVersion)
                + ",feedbackPresent=" + (request != null && request.feedback != null);
    }
}
