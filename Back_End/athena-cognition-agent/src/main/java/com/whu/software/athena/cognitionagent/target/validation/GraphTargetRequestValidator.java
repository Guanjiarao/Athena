package com.whu.software.athena.cognitionagent.target.validation;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphTargetRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(GraphTargetResolutionRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            return new ValidationResult(List.of(issue(
                    AgentErrorCode.INVALID_REQUEST, null, "request is required")));
        }
        required(request.contractVersion, "contractVersion", issues);
        required(request.nodeVersion, "nodeVersion", issues);
        required(request.runId, "runId", issues);
        required(request.idempotencyKey, "idempotencyKey", issues);
        required(request.contextSnapshotId, "contextSnapshotId", issues);
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.TARGET_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(issue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported contract or node version"));
        }
        if (request.triggerType == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "triggerType", "triggerType is required"));
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST, "graph", graphError));
        }
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "evidence", "accepted canonical evidence is required"));
        } else {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < request.evidence.size(); i++) {
                CanonicalEvidence item = request.evidence.get(i);
                if (item == null || blank(item.evidenceId) || item.sourceType == null
                        || item.factLevel == null || blank(item.summary)
                        || blank(item.contentFingerprint)) {
                    issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                            "evidence[" + i + "]", "canonical evidence is incomplete"));
                } else if (!ids.add(item.evidenceId)) {
                    issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                            "evidence[" + i + "].evidenceId", "evidence ids must be unique"));
                }
            }
        }
        if (request.suggestedTopicTitle != null
                && request.suggestedTopicTitle.length() > 80) {
            issues.add(issue(AgentErrorCode.TEXT_TOO_LONG,
                    "suggestedTopicTitle", "suggested topic title must not exceed 80 characters"));
        }
        return new ValidationResult(List.copyOf(issues));
    }

    private void required(String value, String field, List<ValidationIssue> issues) {
        if (blank(value)) issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                field, field + " is required"));
    }

    private ValidationIssue issue(AgentErrorCode code, String field, String message) {
        return new ValidationIssue(code, field, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
