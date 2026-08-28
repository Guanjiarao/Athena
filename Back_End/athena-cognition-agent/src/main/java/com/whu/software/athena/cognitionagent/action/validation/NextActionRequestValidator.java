package com.whu.software.athena.cognitionagent.action.validation;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

public class NextActionRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(NextActionPlanningRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(issue("request", "request is required"));
            return new ValidationResult(issues);
        }
        required(issues, request.runId, "runId");
        required(issues, request.idempotencyKey, "idempotencyKey");
        required(issues, request.contextSnapshotId, "contextSnapshotId");
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "contractVersion", "unsupported contract version"));
        }
        if (!GraphContract.ACTION_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported node version"));
        }
        if (request.triggerType == null) issues.add(issue("triggerType", "triggerType is required"));
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) issues.add(issue("graph", graphError));
        if (request.scope == null) issues.add(issue("scope", "scope is required"));
        if (request.semanticDraft == null) {
            issues.add(issue("semanticDraft", "semanticDraft is required"));
        }
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(issue("evidence", "at least one evidence item is required"));
        }
        if (request.scope != null && request.scope.route != GraphUpdateRoute.CREATE_BRANCH
                && request.scope.route != GraphUpdateRoute.UPDATE_EXISTING) {
            issues.add(issue("scope.route", "action planning requires a create or update route"));
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
