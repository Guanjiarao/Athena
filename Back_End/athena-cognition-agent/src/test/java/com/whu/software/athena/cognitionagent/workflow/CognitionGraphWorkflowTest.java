package com.whu.software.athena.cognitionagent.workflow;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CognitionGraphWorkflowTest {

    @Autowired CognitionGraphWorkflow workflow;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void firstBranchReachesGuardedProposalWithoutMutatingGraph() {
        GraphUpdatePreparationRequest request = request();
        request.graph = GraphTestFixtures.emptyGraph();
        long originalVersion = request.graph.graphVersion;
        int originalNodes = request.graph.nodes.size();

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(GraphPreparationStatus.PROPOSAL_READY, response.status,
                guardError(response));
        assertEquals(GraphSemanticUpdateStatus.SUCCEEDED, response.semanticResult.status);
        assertEquals("HUMAN_CONFIRMATION", response.nextNodeId);
        assertEquals(originalVersion, request.graph.graphVersion);
        assertEquals(originalNodes, request.graph.nodes.size());
        assertEquals("READY_FOR_CONFIRMATION", response.proposal.status.name());
        assertTrue(meterRegistry.get("athena.cognition.graph.node.runs").counters()
                .stream().mapToDouble(counter -> counter.count()).sum() >= 4.0);
    }

    @Test
    void existingBranchProducesIncrementalProposalInsteadOfNewGraph() {
        GraphUpdatePreparationRequest request = request();
        request.graph = GraphTestFixtures.graphWithTwoTopics();

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(GraphPreparationStatus.PROPOSAL_READY, response.status,
                guardError(response));
        assertEquals("topic_mood", response.targetResult.targetTopicId);
        assertEquals(4, response.scopeResult.scope.baseGraphVersion);
        assertEquals(4, request.graph.graphVersion);
        assertTrue(response.proposal.operations.stream().noneMatch(operation ->
                operation.node != null
                        && operation.node.type.name().equals("TOPIC")
                        && operation.operationType.name().equals("ADD_NODE")));
    }

    @Test
    void repeatedEvidenceStopsAtNoChange() {
        GraphUpdatePreparationRequest request = request();
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        GraphUpdatePreparationResponse first = workflow.prepare(request);
        request.existingEvidence = first.evidenceResult.acceptedEvidence;

        GraphUpdatePreparationResponse second = workflow.prepare(request);

        assertEquals(GraphPreparationStatus.NO_CHANGE, second.status);
        assertNull(second.targetResult);
        assertNull(second.semanticResult);
    }

    private GraphUpdatePreparationRequest request() {
        GraphUpdatePreparationRequest value = new GraphUpdatePreparationRequest();
        value.runId = "run_workflow_1";
        value.idempotencyKey = "clue_1:graph-workflow-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_workflow_1";
        value.candidates.add(GraphTestFixtures.relatedCandidate("evidence_1", "clue_1"));
        value.suggestedTopicTitle = "经前情绪变化";
        return value;
    }

    private String guardError(GraphUpdatePreparationResponse response) {
        if (response.patchGuardResult == null || response.patchGuardResult.error == null) {
            return "no patch guard error";
        }
        return response.patchGuardResult.error.field + ": "
                + response.patchGuardResult.error.message;
    }
}
