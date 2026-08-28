package com.whu.software.athena.cognitionagent.guard.validation;

import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphPatchOperation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GraphPatchGuardRequestValidator {

    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public ValidationResult validate(GraphPatchGuardRequest request) {
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
        if (!GraphContract.PATCH_GUARD_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(version("nodeVersion"));
        }
        if (request.triggerType == null) issues.add(issue("triggerType", "triggerType is required"));
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) issues.add(issue("graph", graphError));
        if (request.scope == null) issues.add(issue("scope", "scope is required"));
        if (request.evidence == null || request.evidence.isEmpty()) {
            issues.add(issue("evidence", "at least one evidence item is required"));
        }
        validateProposal(issues, request.proposal, request.triggerType);
        return new ValidationResult(issues);
    }

    private void validateProposal(List<ValidationIssue> issues,
                                  GraphUpdateProposal proposal,
                                  GraphTriggerType triggerType) {
        if (proposal == null) {
            issues.add(issue("proposal", "proposal is required"));
            return;
        }
        if (!GraphContract.PROPOSAL_SCHEMA_VERSION.equals(proposal.proposalSchemaVersion)) {
            issues.add(version("proposal.proposalSchemaVersion"));
        }
        String expectedWorkflowVersion = triggerType == GraphTriggerType.ACTION_FEEDBACK
                ? GraphContract.FEEDBACK_WORKFLOW_VERSION : GraphContract.WORKFLOW_VERSION;
        if (!expectedWorkflowVersion.equals(proposal.workflowVersion)) {
            issues.add(version("proposal.workflowVersion"));
        }
        required(issues, proposal.proposalId, "proposal.proposalId");
        required(issues, proposal.graphId, "proposal.graphId");
        if (proposal.baseGraphVersion < 0) {
            issues.add(issue("proposal.baseGraphVersion", "baseGraphVersion must not be negative"));
        }
        if (proposal.status == null) issues.add(issue("proposal.status", "status is required"));
        if (proposal.route == null) issues.add(issue("proposal.route", "route is required"));
        if (proposal.evidenceIds == null) {
            issues.add(issue("proposal.evidenceIds", "evidenceIds are required"));
        } else if (proposal.evidenceIds.size() != new HashSet<>(proposal.evidenceIds).size()) {
            issues.add(issue("proposal.evidenceIds", "evidenceIds must be unique"));
        }
        if (proposal.operations == null || proposal.operations.isEmpty()
                || proposal.operations.size() > 100) {
            issues.add(issue("proposal.operations", "operations must contain 1-100 items"));
        } else {
            for (int index = 0; index < proposal.operations.size(); index++) {
                GraphPatchOperation operation = proposal.operations.get(index);
                if (operation == null || operation.operationType == null) {
                    issues.add(issue("proposal.operations[" + index + "]",
                            "operationType is required"));
                } else if (operation.evidenceIds == null) {
                    issues.add(issue("proposal.operations[" + index + "].evidenceIds",
                            "evidenceIds are required"));
                } else if (operation.evidenceIds.size()
                        != new HashSet<>(operation.evidenceIds).size()) {
                    issues.add(issue("proposal.operations[" + index + "].evidenceIds",
                            "evidenceIds must be unique"));
                }
            }
        }
        if (proposal.changeSummary != null && proposal.changeSummary.length() > 1000) {
            issues.add(issue("proposal.changeSummary", "changeSummary is too long"));
        }
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
