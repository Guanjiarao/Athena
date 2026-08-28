package com.whu.software.athena.cognitionagent.intent.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentModelOutputSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentModelOutputSchemaValidator validator = new IntentModelOutputSchemaValidator();

    @Test
    void acceptsTheExactTwoFieldContract() throws Exception {
        SchemaValidationResult result = validator.validate(objectMapper.readTree("""
                {"suggestedIntent":"QUESTION","rationale":"The user asks a question."}
                """));

        assertTrue(result.valid());
    }

    @Test
    void rejectsMissingUnknownAndInvalidFields() throws Exception {
        SchemaValidationResult missing = validator.validate(objectMapper.readTree("""
                {"suggestedIntent":"QUESTION"}
                """));
        SchemaValidationResult unknown = validator.validate(objectMapper.readTree("""
                {"suggestedIntent":"QUESTION","rationale":"Valid","diagnosis":"x"}
                """));
        SchemaValidationResult invalidEnum = validator.validate(objectMapper.readTree("""
                {"suggestedIntent":"MAYBE","rationale":"Valid"}
                """));

        assertFalse(missing.valid());
        assertFalse(unknown.valid());
        assertFalse(invalidEnum.valid());
    }
}
