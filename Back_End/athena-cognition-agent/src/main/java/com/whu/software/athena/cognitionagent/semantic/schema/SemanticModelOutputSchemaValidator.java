package com.whu.software.athena.cognitionagent.semantic.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SemanticModelOutputSchemaValidator {

    private static final Set<String> ROOT_FIELDS =
            Set.of("topicTitle", "stageUnderstanding", "stageUnderstandingEvidenceIds",
                    "changes", "changeSummary");
    private static final Set<String> CHANGE_FIELDS =
            Set.of("changeType", "nodeType", "targetNodeId", "content", "evidenceIds");
    private static final Set<String> CHANGE_TYPES = Set.of("ADD", "REVISE", "NO_CHANGE");
    private static final Set<String> NODE_TYPES = Set.of(
            "SELF_REPORTED_FACT", "PATTERN_HYPOTHESIS", "OPEN_QUESTION");

    public SchemaValidationResult validate(JsonNode output) {
        List<String> violations = new ArrayList<>();
        if (output == null || !output.isObject()) {
            return SchemaValidationResult.fail("model output must be an object");
        }
        closedObject(output, ROOT_FIELDS, "root", violations);
        text(output.get("topicTitle"), 1, 80, "topicTitle", violations);
        text(output.get("stageUnderstanding"), 1, 1000,
                "stageUnderstanding", violations);
        stringArray(output.get("stageUnderstandingEvidenceIds"),
                "stageUnderstandingEvidenceIds", false, violations);
        text(output.get("changeSummary"), 1, 500, "changeSummary", violations);
        JsonNode changes = output.get("changes");
        if (changes == null || !changes.isArray() || changes.size() > 12) {
            violations.add("changes must be an array with at most 12 items");
        } else {
            for (int i = 0; i < changes.size(); i++) {
                int changeIndex = i;
                JsonNode change = changes.get(i);
                if (!change.isObject()) {
                    violations.add("changes[" + i + "] must be an object");
                    continue;
                }
                closedObject(change, CHANGE_FIELDS, "changes[" + i + "]", violations);
                if (!change.path("changeType").isTextual()
                        || !CHANGE_TYPES.contains(change.path("changeType").asText())) {
                    violations.add("changes[" + i + "].changeType is invalid");
                }
                if (!change.path("nodeType").isTextual()
                        || !NODE_TYPES.contains(change.path("nodeType").asText())) {
                    violations.add("changes[" + i + "].nodeType is invalid");
                }
                JsonNode target = change.get("targetNodeId");
                if (target == null || !(target.isNull() || target.isTextual())) {
                    violations.add("changes[" + i + "].targetNodeId must be string or null");
                }
                text(change.get("content"), 1, 1000,
                        "changes[" + i + "].content", violations);
                JsonNode evidenceIds = change.get("evidenceIds");
                if (evidenceIds == null || !evidenceIds.isArray()) {
                    violations.add("changes[" + i + "].evidenceIds must be an array");
                } else {
                    Set<String> ids = new HashSet<>();
                    evidenceIds.forEach(id -> {
                        if (!id.isTextual() || id.asText().isBlank() || !ids.add(id.asText())) {
                            violations.add("changes[" + changeIndex
                                    + "].evidenceIds must contain unique strings");
                        }
                    });
                }
            }
        }
        return violations.isEmpty() ? SchemaValidationResult.pass()
                : new SchemaValidationResult(false, violations);
    }

    private void closedObject(JsonNode value,
                              Set<String> expected,
                              String field,
                              List<String> violations) {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        if (!missing.isEmpty()) violations.add(field + " missing fields: " + missing);
        Set<String> extra = new HashSet<>(actual);
        extra.removeAll(expected);
        if (!extra.isEmpty()) violations.add(field + " has unknown fields: " + extra);
    }

    private void text(JsonNode value, int min, int max,
                      String field, List<String> violations) {
        if (value == null || !value.isTextual() || value.asText().isBlank()
                || value.asText().length() < min || value.asText().length() > max) {
            violations.add(field + " must be " + min + "-" + max + " characters");
        }
    }

    private void stringArray(JsonNode value,
                             String field,
                             boolean allowEmpty,
                             List<String> violations) {
        if (value == null || !value.isArray() || (!allowEmpty && value.isEmpty())) {
            violations.add(field + " must be a non-empty string array");
            return;
        }
        Set<String> values = new HashSet<>();
        value.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank() || !values.add(item.asText())) {
                violations.add(field + " must contain unique non-empty strings");
            }
        });
    }
}
