package com.whu.software.athena.cognitionagent.patch.validation;

import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyRequest;

import java.util.ArrayList;
import java.util.List;

public class GraphPatchAssemblyRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(GraphPatchAssemblyRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(issue("request", "request is required"));
            return new ValidationResult(issues);
        }
        required(issues, request.runId, "runId");
        required(issues, request.idempotencyKey, "idempotencyKey");
        required(issues, request.contextSnapshotId, "contextSnapshotId");
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)) {
            issues.add(version("contractVersion"));
        }
        if (!GraphContract.PATCH_ASSEMBLY_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(version("nodeVersion"));
        }
        if (request.triggerType == null) issues.add(issue("triggerType", "triggerType is required"));
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) issues.add(issue("graph", graphError));
        if (request.scope == null) issues.add(issue("scope", "scope is required"));
        if (request.semanticDraft == null) {
            issues.add(issue("semanticDraft", "semanticDraft is required"));
        }
        if (request.actionPlan == null) issues.add(issue("actionPlan", "actionPlan is required"));
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(issue("evidence", "at least one evidence item is required"));
        }
        if (request.scope != null && request.scope.route != GraphUpdateRoute.CREATE_BRANCH
                && request.scope.route != GraphUpdateRoute.UPDATE_EXISTING) {
            issues.add(issue("scope.route", "patch assembly requires create or update route"));
        }
        if (request.scope != null && request.graph != null
                && request.scope.baseGraphVersion != request.graph.graphVersion) {
            issues.add(new ValidationIssue(AgentErrorCode.GRAPH_VERSION_CONFLICT,
                    "scope.baseGraphVersion", "scope graph version is stale"));
        }
        return new ValidationResult(issues);
    }

    private void required(List<ValidationIssue> issues, String value, String field) {
        if (value == null || value.isBlank()) issues.add(issue(field, field + " is required"));
    }

    private ValidationIssue issue(String field, String message) {
        return new ValidationIssue(AgentErrorCode.INVALID_REQUEST, field, message);
    }

    private ValidationIssue version(String field) {
        return new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                field, "unsupported version");
    }
}
