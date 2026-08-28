package com.whu.software.athena.cognitionagent.feedback.validation;

import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationRequest;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class ActionFeedbackNormalizationRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(ActionFeedbackNormalizationRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(issue("request", "request is required"));
            return new ValidationResult(issues);
        }
        required(issues, request.runId, "runId");
        required(issues, request.idempotencyKey, "idempotencyKey");
        required(issues, request.contextSnapshotId, "contextSnapshotId");
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.FEEDBACK_NORMALIZATION_NODE_VERSION.equals(
                request.nodeVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported contract or node version"));
        }
        if (request.triggerType != GraphTriggerType.ACTION_FEEDBACK) {
            issues.add(issue("triggerType", "triggerType must be ACTION_FEEDBACK"));
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) issues.add(issue("graph", graphError));
        if (request.existingEvidence == null) {
            issues.add(issue("existingEvidence", "existingEvidence is required"));
        }
        if (request.feedback == null) {
            issues.add(issue("feedback", "feedback is required"));
        } else {
            required(issues, request.feedback.feedbackId, "feedback.feedbackId");
            required(issues, request.feedback.actionId, "feedback.actionId");
            required(issues, request.feedback.occurredAt, "feedback.occurredAt");
            if (request.feedback.result == null) {
                issues.add(issue("feedback.result", "feedback result is required"));
            }
            if (request.feedback.note != null && request.feedback.note.length() > 500) {
                issues.add(new ValidationIssue(AgentErrorCode.TEXT_TOO_LONG,
                        "feedback.note", "feedback note must not exceed 500 characters"));
            }
            if (request.feedback.feedbackId != null
                    && request.feedback.feedbackId.length() > 128) {
                issues.add(issue("feedback.feedbackId",
                        "feedbackId must not exceed 128 characters"));
            }
            if (request.feedback.actionId != null
                    && request.feedback.actionId.length() > 128) {
                issues.add(issue("feedback.actionId",
                        "actionId must not exceed 128 characters"));
            }
            if (request.feedback.occurredAt != null
                    && !request.feedback.occurredAt.isBlank()) {
                try {
                    OffsetDateTime.parse(request.feedback.occurredAt);
                } catch (DateTimeParseException exception) {
                    issues.add(issue("feedback.occurredAt",
                            "occurredAt must be an ISO-8601 timestamp with offset"));
                }
            }
        }
        return new ValidationResult(issues);
    }

    private void required(List<ValidationIssue> issues, String value, String field) {
        if (value == null || value.isBlank()) issues.add(issue(field, field + " is required"));
    }

    private ValidationIssue issue(String field, String message) {
        return new ValidationIssue(AgentErrorCode.INVALID_REQUEST, field, message);
    }
}
