package com.whu.software.athena.cognitionagent.evidence.validation;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

public class EvidenceRequestValidator {

    public ValidationResult validate(EvidenceCanonicalizationRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(issue("request", "request is required"));
            return new ValidationResult(issues);
        }
        required(issues, request.contractVersion, "contractVersion");
        required(issues, request.nodeVersion, "nodeVersion");
        required(issues, request.runId, "runId");
        required(issues, request.idempotencyKey, "idempotencyKey");
        required(issues, request.contextSnapshotId, "contextSnapshotId");
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.EVIDENCE_NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported contract or node version"));
        }
        if (request.triggerType == null) {
            issues.add(issue("triggerType", "triggerType is required"));
        }
        if (request.candidates == null || request.candidates.isEmpty()) {
            issues.add(issue("candidates", "at least one evidence candidate is required"));
            return new ValidationResult(issues);
        }
        if (request.candidates.size() > 50) {
            issues.add(new ValidationIssue(AgentErrorCode.INVALID_REQUEST,
                    "candidates", "at most 50 evidence candidates are allowed"));
        }
        for (int i = 0; i < request.candidates.size(); i++) {
            validateCandidate(issues, request.candidates.get(i), i);
        }
        return new ValidationResult(issues);
    }

    private void validateCandidate(List<ValidationIssue> issues,
                                   EvidenceCandidate value,
                                   int index) {
        String field = "candidates[" + index + "]";
        if (value == null) {
            issues.add(issue(field, "candidate is required"));
            return;
        }
        required(issues, value.evidenceId, field + ".evidenceId");
        required(issues, value.sourceId, field + ".sourceId");
        required(issues, value.summary, field + ".summary");
        if (value.sourceType == null) issues.add(issue(field + ".sourceType", "sourceType is required"));
        if (value.intent != ClueIntent.RELATED) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_SOURCE_TYPE,
                    field + ".intent", "node 2 only accepts RELATED evidence routed by node 1"));
        }
        if (value.sourceType != null && value.sourceType.name().equals("ARTICLE_HIGHLIGHT")
                && value.relationType == null) {
            issues.add(issue(field + ".relationType",
                    "article evidence requires the user's relation type"));
        }
        if (value.sourceType == EvidenceSourceType.ACTION_FEEDBACK
                && (value.relatedActionId == null || value.relatedActionId.isBlank()
                || value.feedbackResult == null)) {
            issues.add(issue(field + ".relatedActionId",
                    "action feedback requires relatedActionId and feedbackResult"));
        }
        if (value.relationType == RelationType.KNOWLEDGE_ONLY) {
            issues.add(new ValidationIssue(AgentErrorCode.UNSUPPORTED_SOURCE_TYPE,
                    field + ".relationType", "knowledge-only content cannot enter the body graph"));
        }
        if (value.summary != null && value.summary.length() > 2000) {
            issues.add(new ValidationIssue(AgentErrorCode.TEXT_TOO_LONG,
                    field + ".summary", "summary must not exceed 2000 characters"));
        }
        if (value.severity != null && (value.severity < 0 || value.severity > 10)) {
            issues.add(new ValidationIssue(AgentErrorCode.INVALID_REQUEST,
                    field + ".severity", "severity must be between 0 and 10"));
        }
    }

    private void required(List<ValidationIssue> issues, String value, String field) {
        if (value == null || value.isBlank()) issues.add(issue(field, field + " is required"));
    }

    private ValidationIssue issue(String field, String message) {
        return new ValidationIssue(AgentErrorCode.MISSING_REQUIRED_FIELD, field, message);
    }
}
