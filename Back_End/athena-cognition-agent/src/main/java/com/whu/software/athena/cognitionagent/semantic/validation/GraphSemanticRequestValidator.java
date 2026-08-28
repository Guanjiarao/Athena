package com.whu.software.athena.cognitionagent.semantic.validation;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphSemanticRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(GraphSemanticUpdateRequest request) {
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
                || !GraphContract.SEMANTIC_NODE_VERSION.equals(request.nodeVersion)) {
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
        if (request.scope == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "scope", "scope from node 4 is required"));
            return new ValidationResult(List.copyOf(issues));
        }
        if (request.graph != null && (!request.graph.graphId.equals(request.scope.graphId)
                || request.graph.graphVersion != request.scope.baseGraphVersion)) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST, "scope.baseGraphVersion",
                    "scope must refer to the supplied graph id and version"));
        }
        if (request.scope.route != GraphUpdateRoute.UPDATE_EXISTING
                && request.scope.route != GraphUpdateRoute.CREATE_BRANCH) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST, "scope.route",
                    "semantic generation requires UPDATE_EXISTING or CREATE_BRANCH"));
        }
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "evidence", "canonical evidence is required"));
        } else {
            Set<String> ids = new HashSet<>();
            for (CanonicalEvidence item : request.evidence) {
                if (item != null && item.evidenceId != null) ids.add(item.evidenceId);
            }
            if (!ids.containsAll(request.scope.selectedEvidenceIds)) {
                issues.add(issue(AgentErrorCode.INVALID_REQUEST, "scope.selectedEvidenceIds",
                        "every selected evidence id must exist in canonical evidence"));
            }
        }
        return new ValidationResult(List.copyOf(issues));
    }

    private void required(String value, String field, List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    field, field + " is required"));
        }
    }

    private ValidationIssue issue(AgentErrorCode code, String field, String message) {
        return new ValidationIssue(code, field, message);
    }
}
