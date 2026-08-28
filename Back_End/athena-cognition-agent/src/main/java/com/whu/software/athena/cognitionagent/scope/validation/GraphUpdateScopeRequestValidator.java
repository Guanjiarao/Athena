package com.whu.software.athena.cognitionagent.scope.validation;

import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;

import java.util.ArrayList;
import java.util.List;

public class GraphUpdateScopeRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(GraphUpdateScopeRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            return new ValidationResult(List.of(new ValidationIssue(
                    AgentErrorCode.INVALID_REQUEST, null, "request is required")));
        }
        required(request.contractVersion, "contractVersion", issues);
        required(request.nodeVersion, "nodeVersion", issues);
        required(request.runId, "runId", issues);
        required(request.idempotencyKey, "idempotencyKey", issues);
        required(request.contextSnapshotId, "contextSnapshotId", issues);
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.SCOPE_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported contract or node version"));
        }
        if (request.triggerType == null) {
            issues.add(new ValidationIssue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "triggerType", "triggerType is required"));
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) {
            issues.add(new ValidationIssue(
                    AgentErrorCode.INVALID_REQUEST, "graph", graphError));
        }
        if (request.targetRoute == null) {
            issues.add(new ValidationIssue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "targetRoute", "targetRoute is required"));
        }
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(new ValidationIssue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "evidence", "canonical evidence is required"));
        }
        return new ValidationResult(List.copyOf(issues));
    }

    private void required(String value, String field, List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(new ValidationIssue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    field, field + " is required"));
        }
    }
}
