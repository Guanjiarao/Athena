package com.whu.software.athena.cognitionagent.intent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.contract.CluePayload;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IntentSchemaContractTest {

    private static final String SCHEMA_ROOT = "/schemas/intent-evidence-v1/";
    private static final Set<String> MODEL_FIELDS = Set.of(
            "explicitIntent",
            "relationType",
            "helpRequestType",
            "articleTitle",
            "selectedText",
            "questionType",
            "questionText",
            "cycleRelation");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void schemasAreDraft202012DocumentsWithClosedObjects() throws Exception {
        for (String file : Set.of(
                "intent-classification-request.schema.json",
                "intent-classification-response.schema.json",
                "intent-model-context.schema.json",
                "intent-model-output.schema.json")) {
            JsonNode schema = schema(file);
            assertEquals("https://json-schema.org/draft/2020-12/schema", schema.path("$schema").asText());
            assertFalse(schema.path("additionalProperties").asBoolean(true));
        }
    }

    @Test
    void requestAndResponseSchemasMatchJavaContractFields() throws Exception {
        JsonNode request = schema("intent-classification-request.schema.json");
        JsonNode response = schema("intent-classification-response.schema.json");

        assertEquals(publicFields(IntentClassificationRequest.class),
                propertyNames(request.path("properties")));
        assertEquals(publicFields(CluePayload.class),
                propertyNames(request.path("$defs").path("clue").path("properties")));
        assertEquals(publicFields(IntentClassificationResponse.class),
                propertyNames(response.path("properties")));
    }

    @Test
    void modelContextSchemaMatchesTheDedicatedJavaBoundaryExactly() throws Exception {
        JsonNode schema = schema("intent-model-context.schema.json");
        Set<String> javaFields = new HashSet<>();
        Arrays.stream(IntentModelContext.class.getRecordComponents())
                .map(component -> component.getName())
                .forEach(javaFields::add);

        assertEquals(MODEL_FIELDS, javaFields);
        assertEquals(MODEL_FIELDS, propertyNames(schema.path("properties")));
        assertEquals(MODEL_FIELDS, stringValues(schema.path("required")));
    }

    @Test
    void rawModelOutputSchemaAllowsOnlyIntentAndRationale() throws Exception {
        JsonNode schema = schema("intent-model-output.schema.json");
        Set<String> expected = Set.of("suggestedIntent", "rationale");

        assertEquals(expected, propertyNames(schema.path("properties")));
        assertEquals(expected, stringValues(schema.path("required")));
        assertEquals(Set.of("RELATED", "QUESTION", "KNOWLEDGE_ONLY"),
                stringValues(schema.path("properties").path("suggestedIntent").path("enum")));
        assertFalse(schema.path("additionalProperties").asBoolean(true));
    }

    private JsonNode schema(String file) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(SCHEMA_ROOT + file)) {
            assertNotNull(input, "missing schema resource: " + file);
            return objectMapper.readTree(input);
        }
    }

    private Set<String> publicFields(Class<?> type) {
        Set<String> fields = new HashSet<>();
        Arrays.stream(type.getFields()).map(Field::getName).forEach(fields::add);
        return fields;
    }

    private Set<String> propertyNames(JsonNode properties) {
        Set<String> names = new HashSet<>();
        properties.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Set<String> stringValues(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.elements().forEachRemaining(value -> values.add(value.asText()));
        return values;
    }
}
