package com.whu.software.athena.cognitionagent.workflow;

import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningDecision;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphOperationType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchSimulationService;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CognitionGraphWorkflowFourPathTest {

    @Autowired CognitionGraphWorkflow workflow;

    @Test
    void firstCreateIncrementalUpdateNoChangeAndConflictAllStopCorrectly() {
        GraphUpdatePreparationRequest firstRequest = request(
                "first", GraphTestFixtures.emptyGraph(), "evidence_first", "Cycle changes");
        GraphUpdatePreparationResponse first = workflow.prepare(firstRequest);

        assertEquals(GraphPreparationStatus.PROPOSAL_READY, first.status);
        assertEquals(GraphUpdateRoute.CREATE_BRANCH, first.proposal.route);
        assertEquals("HUMAN_CONFIRMATION", first.nextNodeId);
        assertEquals(1, first.proposal.operations.stream().filter(operation ->
                operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.TOPIC).count());
        assertEquals(1, first.proposal.operations.stream().filter(operation ->
                operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.ACTION).count());

        PersonalCognitionGraph acceptedGraph = new GraphPatchSimulationService()
                .simulate(firstRequest.graph, first.proposal).simulatedGraph();
        GraphUpdatePreparationRequest updateRequest = request(
                "update", acceptedGraph, "evidence_update", first.proposal.operations.stream()
                        .filter(operation -> operation.node != null
                                && operation.node.type == GraphNodeType.TOPIC)
                        .findFirst().orElseThrow().node.title);
        GraphUpdatePreparationResponse update = workflow.prepare(updateRequest);

        assertEquals(GraphPreparationStatus.PROPOSAL_READY, update.status);
        assertEquals(GraphUpdateRoute.UPDATE_EXISTING, update.proposal.route);
        assertEquals(first.proposal.targetTopicId, update.proposal.targetTopicId);
        assertEquals(ActionPlanningDecision.KEEP_EXISTING, update.actionResult.plan.decision);
        assertTrue(update.proposal.operations.stream().noneMatch(operation ->
                operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.TOPIC));
        assertTrue(update.proposal.operations.stream().noneMatch(operation ->
                operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.ACTION));
        assertEquals(1, acceptedGraph.graphVersion);

        GraphUpdatePreparationRequest noChangeRequest = request(
                "no-change", acceptedGraph, "evidence_first", "Cycle changes");
        noChangeRequest.existingEvidence = first.evidenceResult.acceptedEvidence;
        GraphUpdatePreparationResponse noChange = workflow.prepare(noChangeRequest);

        assertEquals(GraphPreparationStatus.NO_CHANGE, noChange.status);
        assertNull(noChange.targetResult);
        assertNull(noChange.patchAssemblyResult);
        assertNull(noChange.proposal);

        GraphUpdatePreparationRequest conflictRequest = request(
                "conflict", GraphTestFixtures.graphWithTwoTopics(),
                "evidence_conflict", "Unmatched ambiguous topic");
        GraphUpdatePreparationResponse conflict = workflow.prepare(conflictRequest);

        assertEquals(GraphPreparationStatus.NEEDS_CONFIRMATION, conflict.status);
        assertEquals(GraphUpdateRoute.NEEDS_CONFIRMATION, conflict.targetResult.route);
        assertNull(conflict.scopeResult);
        assertNull(conflict.semanticResult);
        assertNull(conflict.actionResult);
        assertNull(conflict.patchAssemblyResult);
        assertNull(conflict.proposal);
        assertNotNull(conflict.targetResult.rationale);
    }

    private GraphUpdatePreparationRequest request(String suffix,
                                                  PersonalCognitionGraph graph,
                                                  String evidenceId,
                                                  String title) {
        GraphUpdatePreparationRequest value = new GraphUpdatePreparationRequest();
        value.runId = "run_path_" + suffix;
        value.idempotencyKey = "clue_path_" + suffix + ":workflow";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_path_" + suffix;
        value.graph = graph;
        value.candidates.add(GraphTestFixtures.relatedCandidate(
                evidenceId, "clue_path_" + suffix));
        value.suggestedTopicTitle = title;
        value.requestedAt = "2026-08-27T12:00:00+08:00";
        return value;
    }
}
