package com.whu.software.athena.cognitionagent.target;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetContextBuilder;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetModelContext;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphTargetModelContextBoundaryTest {

    @Test
    void modelDoesNotReceiveGraphMetadataOrEvidenceSourceIds() throws Exception {
        GraphTargetResolutionRequest request = new GraphTargetResolutionRequest();
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.evidence.add(GraphTestFixtures.declaredEvidence("evidence_1"));
        request.suggestedTopicTitle = "状态变化";
        GraphTargetContextBuilder builder = new GraphTargetContextBuilder();
        GraphTargetModelContext model = builder.buildModelContext(builder.build(request));

        String json = new ObjectMapper().writeValueAsString(model);
        JsonNode root = new ObjectMapper().readTree(json);
        Set<String> fields = new HashSet<>();
        root.fieldNames().forEachRemaining(fields::add);

        assertEquals(Set.of("suggestedTopicTitle", "evidence", "candidateTopics"), fields);
        assertFalse(json.contains("graphVersion"));
        assertFalse(json.contains("contentFingerprint"));
        assertFalse(json.contains("clue_evidence_1"));
        assertFalse(json.contains("createdAt"));
        assertFalse(json.contains("updatedAt"));
    }
}
