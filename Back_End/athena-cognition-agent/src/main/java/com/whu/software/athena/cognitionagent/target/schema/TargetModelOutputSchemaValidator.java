package com.whu.software.athena.cognitionagent.target.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TargetModelOutputSchemaValidator {

    private static final Set<String> FIELDS = Set.of(
            "route", "matchedTopicId", "suggestedTopicTitle", "rationale");
    private static final Set<String> ROUTES = Set.of(
            "UPDATE_EXISTING", "CREATE_BRANCH", "NEEDS_CONFIRMATION");

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
        if (!output.path("route").isTextual()
                || !ROUTES.contains(output.path("route").asText())) {
            violations.add("route is invalid");
        }
        JsonNode matched = output.get("matchedTopicId");
        if (matched == null || !(matched.isNull() || matched.isTextual())) {
            violations.add("matchedTopicId must be string or null");
        }
        JsonNode title = output.get("suggestedTopicTitle");
        if (title == null || !(title.isNull() || title.isTextual())
                || title.isTextual() && title.asText().length() > 80) {
            violations.add("suggestedTopicTitle must be null or at most 80 characters");
        }
        JsonNode rationale = output.get("rationale");
        if (rationale == null || !rationale.isTextual() || rationale.asText().isBlank()
                || rationale.asText().length() > 500) {
            violations.add("rationale must be 1-500 characters");
        }
        return violations.isEmpty() ? SchemaValidationResult.pass()
                : new SchemaValidationResult(false, violations);
    }
}
