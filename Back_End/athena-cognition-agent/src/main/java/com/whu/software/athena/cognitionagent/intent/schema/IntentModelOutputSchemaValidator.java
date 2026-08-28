package com.whu.software.athena.cognitionagent.intent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelSuggestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime validator for the raw JSON described by intent-model-output.schema.json.
 * It intentionally validates only the model's two-field output, before metadata
 * such as provider and token usage is attached.
 */
public class IntentModelOutputSchemaValidator {

    private static final String SCHEMA_RESOURCE =
            "/schemas/intent-evidence-v1/intent-model-output.schema.json";

    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public IntentModelOutputSchemaValidator() {
        this(new ObjectMapper());
    }

    IntentModelOutputSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema(objectMapper);
    }

    public SchemaValidationResult validate(JsonNode output) {
        if (output == null || !output.isObject()) {
            return SchemaValidationResult.fail("model output must be a JSON object");
        }

        List<String> violations = new ArrayList<>();
        JsonNode properties = schema.path("properties");
        Set<String> allowedFields = fieldNames(properties);
        Set<String> requiredFields = textValues(schema.path("required"));
        Set<String> actualFields = new HashSet<>();
        output.fieldNames().forEachRemaining(actualFields::add);
        Set<String> missingFields = new HashSet<>(requiredFields);
        missingFields.removeAll(actualFields);
        if (!missingFields.isEmpty()) {
            violations.add("missing required field(s): " + missingFields);
        }
        Set<String> extraFields = new HashSet<>(actualFields);
        extraFields.removeAll(allowedFields);
        if (!schema.path("additionalProperties").asBoolean(true) && !extraFields.isEmpty()) {
            violations.add("unknown field(s): " + extraFields);
        }

        JsonNode suggestedIntent = output.get("suggestedIntent");
        Set<String> allowedIntents = textValues(properties.path("suggestedIntent").path("enum"));
        if (suggestedIntent == null || !suggestedIntent.isTextual()
                || !allowedIntents.contains(suggestedIntent.textValue())) {
            violations.add("suggestedIntent must be RELATED, QUESTION, or KNOWLEDGE_ONLY");
        }

        JsonNode rationale = output.get("rationale");
        int minLength = properties.path("rationale").path("minLength").asInt(0);
        int maxLength = properties.path("rationale").path("maxLength").asInt(Integer.MAX_VALUE);
        if (rationale == null || !rationale.isTextual()
                || rationale.textValue().length() < minLength
                || rationale.textValue().isBlank()
                || rationale.textValue().length() > maxLength) {
            violations.add("rationale must be a non-empty string of at most 500 characters");
        }
        return violations.isEmpty()
                ? SchemaValidationResult.pass()
                : new SchemaValidationResult(false, violations);
    }

    public SchemaValidationResult validate(IntentModelSuggestion suggestion) {
        if (suggestion == null) {
            return SchemaValidationResult.fail("model suggestion must not be null");
        }
        ObjectNode rawOutput = objectMapper.createObjectNode();
        if (suggestion.suggestedIntent() != null) {
            rawOutput.put("suggestedIntent", suggestion.suggestedIntent().name());
        }
        if (suggestion.rationale() != null) {
            rawOutput.put("rationale", suggestion.rationale());
        }
        return validate(rawOutput);
    }

    private JsonNode loadSchema(ObjectMapper objectMapper) {
        try (InputStream input = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing schema resource: " + SCHEMA_RESOURCE);
            }
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load model output schema", exception);
        }
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> values = new HashSet<>();
        object.fieldNames().forEachRemaining(values::add);
        return values;
    }

    private Set<String> textValues(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.elements().forEachRemaining(value -> values.add(value.asText()));
        return values;
    }
}
