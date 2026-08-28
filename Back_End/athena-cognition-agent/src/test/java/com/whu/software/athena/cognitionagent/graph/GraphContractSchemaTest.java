package com.whu.software.athena.cognitionagent.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphContractSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allGraphSchemasUseDraft202012AndCloseRootObjects() throws Exception {
        List<String> files = List.of(
                "/schemas/personal-cognition-graph-v1/personal-cognition-graph.schema.json",
                "/schemas/personal-cognition-graph-v1/graph-update-proposal.schema.json",
                "/schemas/evidence-canonicalization-v1/evidence-canonicalization-request.schema.json",
                "/schemas/evidence-canonicalization-v1/evidence-canonicalization-response.schema.json",
                "/schemas/graph-target-resolution-v1/graph-target-resolution-request.schema.json",
                "/schemas/graph-target-resolution-v1/graph-target-resolution-response.schema.json",
                "/schemas/graph-target-resolution-v1/graph-target-model-context.schema.json",
                "/schemas/graph-target-resolution-v1/graph-target-model-output.schema.json",
                "/schemas/graph-update-scope-v1/graph-update-scope-request.schema.json",
                "/schemas/graph-update-scope-v1/graph-update-scope-response.schema.json",
                "/schemas/graph-update-scope-v1/graph-update-scope.schema.json",
                "/schemas/graph-semantic-update-v1/graph-semantic-update-request.schema.json",
                "/schemas/graph-semantic-update-v1/graph-semantic-update-response.schema.json",
                "/schemas/graph-semantic-update-v1/graph-semantic-model-context.schema.json",
                "/schemas/graph-semantic-update-v1/graph-semantic-model-output.schema.json",
                "/schemas/next-action-planning-v1/next-action-planning-request.schema.json",
                "/schemas/next-action-planning-v1/next-action-planning-response.schema.json",
                "/schemas/next-action-planning-v1/next-action-model-context.schema.json",
                "/schemas/next-action-planning-v1/next-action-model-output.schema.json",
                "/schemas/graph-patch-assembly-v1/graph-patch-assembly-request.schema.json",
                "/schemas/graph-patch-assembly-v1/graph-patch-assembly-response.schema.json",
                "/schemas/graph-patch-guard-v1/graph-patch-guard-request.schema.json",
                "/schemas/graph-patch-guard-v1/graph-patch-guard-response.schema.json",
                "/schemas/cognition-graph-workflow-v1/cognition-graph-workflow-request.schema.json",
                "/schemas/cognition-graph-workflow-v1/cognition-graph-workflow-response.schema.json",
                "/schemas/action-feedback-normalization-v1/action-feedback-normalization-request.schema.json",
                "/schemas/action-feedback-normalization-v1/action-feedback-normalization-response.schema.json",
                "/schemas/action-feedback-graph-update-v1/action-feedback-graph-update-request.schema.json",
                "/schemas/action-feedback-graph-update-v1/action-feedback-graph-update-response.schema.json",
                "/schemas/action-feedback-workflow-v1/action-feedback-workflow-request.schema.json",
                "/schemas/action-feedback-workflow-v1/action-feedback-workflow-response.schema.json");
        for (String file : files) {
            try (InputStream input = getClass().getResourceAsStream(file)) {
                assertNotNull(input, "missing schema: " + file);
                JsonNode schema = mapper.readTree(input);
                assertEquals("https://json-schema.org/draft/2020-12/schema",
                        schema.path("$schema").asText());
                assertFalse(schema.path("additionalProperties").asBoolean(true),
                        "root object must be closed: " + file);
            }
        }
    }
}
