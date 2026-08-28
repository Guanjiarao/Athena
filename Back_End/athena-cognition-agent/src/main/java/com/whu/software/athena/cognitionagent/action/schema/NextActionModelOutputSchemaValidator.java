package com.whu.software.athena.cognitionagent.action.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NextActionModelOutputSchemaValidator {

    private static final Set<String> FIELDS = Set.of(
            "actionType", "title", "description", "evidenceIds", "rationale");
    private static final Set<String> ACTION_TYPES = Set.of(
            "RECORD_BODY", "RECORD_MOOD", "RECORD_SLEEP",
            "ANSWER_QUESTION", "CONFIRM_STATUS");

    public SchemaValidationResult validate(JsonNode output) {
        List<String> violations = new ArrayList<>();
        if (output == null || !output.isObject()) {
            return SchemaValidationResult.fail("model output must be an object");
        }
        Set<String> actual = new HashSet<>();
        output.fieldNames().forEachRemaining(actual::add);
        Set<String> missing = new HashSet<>(FIELDS);
        missing.removeAll(actual);
        if (!missing.isEmpty()) violations.add("missing fields: " + missing);
        Set<String> extra = new HashSet<>(actual);
        extra.removeAll(FIELDS);
        if (!extra.isEmpty()) violations.add("unknown fields: " + extra);
        if (!output.path("actionType").isTextual()
                || !ACTION_TYPES.contains(output.path("actionType").asText())) {
            violations.add("actionType is invalid");
        }
        text(output.get("title"), 1, 80, "title", violations);
        text(output.get("description"), 1, 500, "description", violations);
        text(output.get("rationale"), 1, 500, "rationale", violations);
        JsonNode evidence = output.get("evidenceIds");
        if (evidence == null || !evidence.isArray() || evidence.isEmpty()) {
            violations.add("evidenceIds must be a non-empty array");
        } else {
            Set<String> ids = new HashSet<>();
            evidence.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank() || !ids.add(item.asText())) {
                    violations.add("evidenceIds must contain unique non-empty strings");
                }
            });
        }
        return violations.isEmpty() ? SchemaValidationResult.pass()
                : new SchemaValidationResult(false, violations);
    }

    private void text(JsonNode value, int min, int max,
                      String field, List<String> violations) {
        if (value == null || !value.isTextual() || value.asText().isBlank()
                || value.asText().length() < min || value.asText().length() > max) {
            violations.add(field + " must be " + min + "-" + max + " characters");
        }
    }
}
