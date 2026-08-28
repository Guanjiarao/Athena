package com.whu.software.athena.cognitionagent.target;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionResponse;
import com.whu.software.athena.cognitionagent.target.contract.TargetResolutionStatus;
import com.whu.software.athena.cognitionagent.target.service.GraphTargetResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GraphTargetResolutionServiceTest {

    @Autowired GraphTargetResolutionService service;

    @Test
    void userSelectedTargetIsAuthoritative() {
        GraphTargetResolutionRequest request = request();
        request.userSelectedTopicId = "topic_sleep";
        request.suggestedTopicTitle = "经前情绪变化";

        GraphTargetResolutionResponse response = service.resolve(request);

        assertEquals(TargetResolutionStatus.SUCCEEDED, response.status);
        assertEquals(GraphUpdateRoute.UPDATE_EXISTING, response.route);
        assertEquals("topic_sleep", response.targetTopicId);
        assertEquals(DecisionSource.USER_DECLARED, response.decisionSource);
    }

    @Test
    void exactTitleMatchAvoidsModelAndUpdatesExistingBranch() {
        GraphTargetResolutionResponse response = service.resolve(request());

        assertEquals(TargetResolutionStatus.SUCCEEDED, response.status);
        assertEquals("topic_mood", response.targetTopicId);
        assertEquals(DecisionSource.RULE, response.decisionSource);
    }

    @Test
    void ambiguousMockSuggestionRequiresHumanConfirmation() {
        GraphTargetResolutionRequest request = request();
        request.suggestedTopicTitle = "状态变化";

        GraphTargetResolutionResponse response = service.resolve(request);

        assertEquals(TargetResolutionStatus.NEEDS_CONFIRMATION, response.status);
        assertEquals(GraphUpdateRoute.NEEDS_CONFIRMATION, response.route);
    }

    private GraphTargetResolutionRequest request() {
        GraphTargetResolutionRequest value = new GraphTargetResolutionRequest();
        value.runId = "run_target_1";
        value.idempotencyKey = "evidence_1:target-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_target_1";
        value.graph = GraphTestFixtures.graphWithTwoTopics();
        value.evidence.add(GraphTestFixtures.declaredEvidence("evidence_1"));
        value.suggestedTopicTitle = "经前情绪变化";
        return value;
    }
}
