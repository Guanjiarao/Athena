package com.whu.software.athena.cognitionagent.intent.validation;

import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.ClueType;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Validates the saved clue contract before any workflow logic runs. */
public class IntentRequestValidator {

    public ValidationResult validate(IntentClassificationRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (request == null) {
            return new ValidationResult(List.of(issue(
                    AgentErrorCode.INVALID_REQUEST, null, "request must not be null")));
        }

        requireText(request.contractVersion, "contractVersion", issues);
        requireText(request.nodeVersion, "nodeVersion", issues);
        requireText(request.runId, "runId", issues);
        requireText(request.idempotencyKey, "idempotencyKey", issues);
        requireText(request.contextSnapshotId, "contextSnapshotId", issues);
        if (request.triggerType == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "triggerType", "triggerType is required"));
        }

        if (!AgentContract.CONTRACT_VERSION.equals(request.contractVersion)) {
            issues.add(issue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "contractVersion", "unsupported contractVersion"));
        }
        if (!AgentContract.NODE_VERSION.equals(request.nodeVersion)) {
            issues.add(issue(AgentErrorCode.UNSUPPORTED_VERSION,
                    "nodeVersion", "unsupported nodeVersion"));
        }

        if (request.clue == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue", "clue is required"));
            return new ValidationResult(List.copyOf(issues));
        }

        validateClue(request.clue, issues);
        return new ValidationResult(List.copyOf(issues));
    }

    private void validateClue(com.whu.software.athena.cognitionagent.intent.contract.CluePayload clue,
                              List<ValidationIssue> issues) {
        requireText(clue.id, "clue.id", issues);
        requireText(clue.articleId, "clue.articleId", issues);
        requireText(clue.articleTitle, "clue.articleTitle", issues);
        requireText(clue.selectedText, "clue.selectedText", issues);
        requireText(clue.originalLabel, "clue.originalLabel", issues);
        requireText(clue.source, "clue.source", issues);
        requireText(clue.createdAt, "clue.createdAt", issues);
        requireText(clue.updatedAt, "clue.updatedAt", issues);
        if (clue.status == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.status", "clue.status is required"));
        }

        if (clue.type == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.type", "clue.type is required"));
        } else if (clue.type != ClueType.ARTICLE_HIGHLIGHT) {
            issues.add(issue(AgentErrorCode.UNSUPPORTED_SOURCE_TYPE,
                    "clue.type", "only ARTICLE_HIGHLIGHT is supported by this node"));
        }

        if (clue.intent == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.intent", "clue.intent is required"));
        }
        if (clue.helpRequestType == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.helpRequestType", "clue.helpRequestType is required"));
        }
        if (clue.cycleRelation == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.cycleRelation", "clue.cycleRelation is required"));
        }

        checkLength(clue.articleTitle, "clue.articleTitle", 200, issues);
        checkLength(clue.selectedText, "clue.selectedText", 3000, issues);
        checkLength(clue.questionText, "clue.questionText", 2000, issues);
        checkLength(clue.suggestedTopicTitle, "clue.suggestedTopicTitle", 200, issues);
        checkLength(clue.originalLabel, "clue.originalLabel", 100, issues);

        if (clue.severity != null && (clue.severity < 0 || clue.severity > 10)) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.severity", "severity must be between 0 and 10"));
        }
        validateDate(clue.occurredAt, "clue.occurredAt", issues);
        validateDate(clue.createdAt, "clue.createdAt", issues);
        validateDate(clue.updatedAt, "clue.updatedAt", issues);

        if (clue.intent != null) {
            switch (clue.intent) {
                case RELATED -> validateRelated(clue, issues);
                case QUESTION -> validateQuestion(clue, issues);
                case KNOWLEDGE_ONLY -> validateKnowledgeOnly(clue, issues);
            }
        }
    }

    private void validateRelated(com.whu.software.athena.cognitionagent.intent.contract.CluePayload clue,
                                 List<ValidationIssue> issues) {
        if (clue.relationType == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.relationType", "relationType is required for RELATED"));
        } else if (clue.relationType == RelationType.KNOWLEDGE_ONLY) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.relationType", "RELATED cannot use KNOWLEDGE_ONLY relation"));
        }
        if (clue.questionType != null || hasText(clue.questionText)) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.questionText", "question fields are only allowed for QUESTION"));
        }
    }

    private void validateQuestion(com.whu.software.athena.cognitionagent.intent.contract.CluePayload clue,
                                   List<ValidationIssue> issues) {
        if (clue.questionType == null) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "clue.questionType", "questionType is required for QUESTION"));
        }
        requireText(clue.questionText, "clue.questionText", issues);
        if (clue.relationType != null) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.relationType", "relationType must be null for QUESTION"));
        }
    }

    private void validateKnowledgeOnly(com.whu.software.athena.cognitionagent.intent.contract.CluePayload clue,
                                       List<ValidationIssue> issues) {
        if (clue.relationType != RelationType.KNOWLEDGE_ONLY) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.relationType", "KNOWLEDGE_ONLY requires KNOWLEDGE_ONLY relation"));
        }
        if (clue.helpRequestType != HelpRequestType.SAVE_ONLY) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.helpRequestType", "KNOWLEDGE_ONLY requires SAVE_ONLY help request"));
        }
        if (clue.questionType != null || hasText(clue.questionText)) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    "clue.questionText", "question fields are only allowed for QUESTION"));
        }
    }

    private void validateDate(String value, String field, List<ValidationIssue> issues) {
        if (!hasText(value)) {
            return;
        }
        try {
            OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            issues.add(issue(AgentErrorCode.INVALID_REQUEST,
                    field, "must be an ISO-8601 offset date-time"));
        }
    }

    private void requireText(String value, String field, List<ValidationIssue> issues) {
        if (!hasText(value)) {
            issues.add(issue(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    field, field + " is required"));
        }
    }

    private void checkLength(String value, String field, int maxLength,
                             List<ValidationIssue> issues) {
        if (value != null && value.length() > maxLength) {
            issues.add(issue(AgentErrorCode.TEXT_TOO_LONG,
                    field, field + " exceeds " + maxLength + " characters"));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ValidationIssue issue(AgentErrorCode code, String field, String message) {
        return new ValidationIssue(code, field, message);
    }
}
