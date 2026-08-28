package com.whu.software.athena.cognitionagent.scope;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeResponse;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeStatus;
import com.whu.software.athena.cognitionagent.scope.service.GraphUpdateScopePlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GraphUpdateScopePlanningServiceTest {

    @Autowired GraphUpdateScopePlanningService service;

    @Test
    void scopeExposesOnlyTargetBranchAndKeepsEvidenceImmutable() {
        GraphUpdateScopeRequest request = new GraphUpdateScopeRequest();
        request.runId = "run_scope_1";
        request.idempotencyKey = "evidence_1:scope-v1";
        request.triggerType = GraphTriggerType.USER_REQUEST;
        request.contextSnapshotId = "ctx_scope_1";
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.evidence.add(GraphTestFixtures.declaredEvidence("evidence_1"));
        request.targetRoute = GraphUpdateRoute.UPDATE_EXISTING;
        request.targetTopicId = "topic_mood";
        request.proposedTopicTitle = "经前情绪变化";

        GraphUpdateScopeResponse response = service.plan(request);

        assertEquals(GraphUpdateScopeStatus.READY, response.status);
        assertTrue(response.scope.readableNodeIds.contains("topic_mood"));
        assertTrue(response.scope.readableNodeIds.contains("hyp_mood"));
        assertFalse(response.scope.readableNodeIds.contains("topic_sleep"));
        assertFalse(response.scope.readableNodeIds.contains("hyp_sleep"));
        assertTrue(response.scope.mutableNodeIds.contains("hyp_mood"));
    }
}
