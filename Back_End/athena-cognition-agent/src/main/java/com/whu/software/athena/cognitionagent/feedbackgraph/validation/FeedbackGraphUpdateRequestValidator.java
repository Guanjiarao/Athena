package com.whu.software.athena.cognitionagent.feedbackgraph.validation;

import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateRequest;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FeedbackGraphUpdateRequestValidator {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(FeedbackGraphUpdateRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(issue("request", "request is required"));
            return new ValidationResult(issues);
        }
        required(issues, request.runId, "runId");
        required(issues, request.idempotencyKey, "idempotencyKey");
        required(issues, request.contextSnapshotId, "contextSnapshotId");
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.FEEDBACK_GRAPH_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported contract or node version"));
        }
        if (request.triggerType != GraphTriggerType.ACTION_FEEDBACK) {
            issues.add(issue("triggerType", "triggerType must be ACTION_FEEDBACK"));
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) issues.add(issue("graph", graphError));
        if (request.normalizedFeedback == null) {
            issues.add(issue("normalizedFeedback", "normalizedFeedback is required"));
        } else {
            required(issues, request.normalizedFeedback.feedbackId,
                    "normalizedFeedback.feedbackId");
            required(issues, request.normalizedFeedback.actionId,
                    "normalizedFeedback.actionId");
            required(issues, request.normalizedFeedback.topicId,
                    "normalizedFeedback.topicId");
            if (request.normalizedFeedback.result == null
                    || request.normalizedFeedback.resultingActionStatus == null) {
                issues.add(issue("normalizedFeedback.result",
                        "feedback result and resulting action status are required"));
            }
            if (request.normalizedFeedback.baseGraphVersion < 0) {
                issues.add(issue("normalizedFeedback.baseGraphVersion",
                        "baseGraphVersion must not be negative"));
            }
            if (request.normalizedFeedback.evidence == null) {
                issues.add(issue("normalizedFeedback.evidence",
                        "canonical action feedback evidence is required"));
            } else {
                validateEvidence(issues, request.normalizedFeedback.evidence,
                        request.normalizedFeedback.feedbackId,
                        request.normalizedFeedback.actionId,
                        request.normalizedFeedback.result);
            }
        }
        return new ValidationResult(issues);
    }

    private void validateEvidence(List<ValidationIssue> issues,
                                  CanonicalEvidence evidence,
                                  String feedbackId,
                                  String actionId,
                                  GraphActionFeedbackResult result) {
        required(issues, evidence.evidenceId,
                "normalizedFeedback.evidence.evidenceId");
        required(issues, evidence.sourceId,
                "normalizedFeedback.evidence.sourceId");
        required(issues, evidence.summary,
                "normalizedFeedback.evidence.summary");
        required(issues, evidence.contentFingerprint,
                "normalizedFeedback.evidence.contentFingerprint");
        required(issues, evidence.occurredAt,
                "normalizedFeedback.evidence.occurredAt");
        required(issues, evidence.relatedActionId,
                "normalizedFeedback.evidence.relatedActionId");
        if (evidence.sourceType != EvidenceSourceType.ACTION_FEEDBACK) {
            issues.add(issue("normalizedFeedback.evidence.sourceType",
                    "feedback evidence sourceType must be ACTION_FEEDBACK"));
        }
        if (result != null && evidence.feedbackResult != result) {
            issues.add(issue("normalizedFeedback.evidence.feedbackResult",
                    "feedback evidence result must match normalized feedback"));
        }
        if (result != null && evidence.factLevel != expectedFactLevel(result)) {
            issues.add(issue("normalizedFeedback.evidence.factLevel",
                    "feedback evidence factLevel does not match its result"));
        }
        if (feedbackId != null && !feedbackId.equals(evidence.sourceId)) {
            issues.add(issue("normalizedFeedback.evidence.sourceId",
                    "feedback evidence sourceId must match feedbackId"));
        }
        if (actionId != null && !actionId.equals(evidence.relatedActionId)) {
            issues.add(issue("normalizedFeedback.evidence.relatedActionId",
                    "feedback evidence relatedActionId must match actionId"));
        }
        if (evidence.contentFingerprint != null
                && !SHA_256.matcher(evidence.contentFingerprint).matches()) {
            issues.add(issue("normalizedFeedback.evidence.contentFingerprint",
                    "contentFingerprint must be a lowercase SHA-256 value"));
        }
        if (evidence.occurredAt != null && !evidence.occurredAt.isBlank()) {
            try {
                OffsetDateTime.parse(evidence.occurredAt);
            } catch (DateTimeParseException exception) {
                issues.add(issue("normalizedFeedback.evidence.occurredAt",
                        "occurredAt must be an ISO-8601 timestamp with offset"));
            }
        }
        if (evidence.cycleRelation != null || evidence.severity != null
                || evidence.resolved != null) {
            issues.add(issue("normalizedFeedback.evidence",
                    "action feedback evidence cannot assert cycle, severity, or resolution fields"));
        }
    }

    private EvidenceFactLevel expectedFactLevel(GraphActionFeedbackResult result) {
        return switch (result) {
            case OCCURRED, NOT_OCCURRED -> EvidenceFactLevel.OBSERVED;
            case UNCERTAIN -> EvidenceFactLevel.QUESTION;
            case SKIPPED -> EvidenceFactLevel.PROCESS_EVENT;
        };
    }

    private void required(List<ValidationIssue> issues, String value, String field) {
        if (value == null || value.isBlank()) issues.add(issue(field, field + " is required"));
    }

    private ValidationIssue issue(String field, String message) {
        return new ValidationIssue(AgentErrorCode.INVALID_REQUEST, field, message);
    }
}
