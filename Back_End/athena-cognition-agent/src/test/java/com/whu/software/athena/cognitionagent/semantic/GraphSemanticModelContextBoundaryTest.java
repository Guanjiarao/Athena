package com.whu.software.athena.cognitionagent.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContextBuilder;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticModelContext;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphSemanticModelContextBoundaryTest {

    @Test
    void modelSeesOnlyTargetSubgraphAndSelectedEvidence() {
        GraphUpdateScope scope = new GraphUpdateScope();
        scope.graphId = "graph_1";
        scope.baseGraphVersion = 4;
        scope.route = GraphUpdateRoute.UPDATE_EXISTING;
        scope.targetTopicId = "topic_mood";
        scope.proposedTopicTitle = "经前情绪变化";
        scope.selectedEvidenceIds = List.of("evidence_1");
        scope.readableNodeIds = List.of("topic_mood", "hyp_mood");
        scope.mutableNodeIds = List.of("topic_mood", "hyp_mood");

        GraphSemanticUpdateRequest request = new GraphSemanticUpdateRequest();
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.evidence = List.of(GraphTestFixtures.declaredEvidence("evidence_1"));
        request.scope = scope;

        GraphSemanticContextBuilder builder = new GraphSemanticContextBuilder();
        GraphSemanticModelContext model =
                builder.buildModelContext(builder.build(request));
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(model);
            JsonNode root = new ObjectMapper().readTree(json);
            Set<String> fields = new HashSet<>();
            root.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("route", "targetTopicId", "targetTopicTitle",
                    "existingNodes", "evidences"), fields);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        assertFalse(json.contains("topic_sleep"));
        assertFalse(json.contains("hyp_sleep"));
        assertFalse(json.contains("graphVersion"));
        assertFalse(json.contains("contentFingerprint"));
        assertFalse(json.contains("sourceId"));
    }
}
