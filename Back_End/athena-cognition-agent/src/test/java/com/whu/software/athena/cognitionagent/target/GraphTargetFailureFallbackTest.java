package com.whu.software.athena.cognitionagent.target;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetModelContext;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionResponse;
import com.whu.software.athena.cognitionagent.target.contract.TargetResolutionStatus;
import com.whu.software.athena.cognitionagent.target.provider.GraphTargetModelProvider;
import com.whu.software.athena.cognitionagent.target.provider.TargetModelSuggestion;
import com.whu.software.athena.cognitionagent.target.service.GraphTargetResolutionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphTargetFailureFallbackTest {

    @Test
    void modelTimeoutFallsBackToHumanConfirmation() {
        GraphTargetModelProvider failing = new GraphTargetModelProvider() {
            @Override public String providerName() { return "failing"; }
            @Override public String modelName() { return "failing-model"; }
            @Override public TargetModelSuggestion resolve(GraphTargetModelContext context) {
                throw new IntentModelProviderException(
                        AgentErrorCode.MODEL_TIMEOUT, "timeout", true);
            }
        };
        GraphTargetResolutionService service = new GraphTargetResolutionService(
                failing, new WorkflowTelemetryRecorder(new SimpleMeterRegistry()));
        GraphTargetResolutionRequest request = new GraphTargetResolutionRequest();
        request.runId = "run_target_timeout";
        request.idempotencyKey = "evidence_1:target-timeout";
        request.triggerType = GraphTriggerType.USER_REQUEST;
        request.contextSnapshotId = "ctx_target_timeout";
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.evidence.add(GraphTestFixtures.declaredEvidence("evidence_1"));
        request.suggestedTopicTitle = "状态变化";

        GraphTargetResolutionResponse response = service.resolve(request);

        assertEquals(TargetResolutionStatus.NEEDS_CONFIRMATION, response.status);
        assertEquals("MODEL_TIMEOUT", response.observation.modelErrorCode);
    }
}
