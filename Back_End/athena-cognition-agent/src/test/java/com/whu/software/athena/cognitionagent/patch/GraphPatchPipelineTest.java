package com.whu.software.athena.cognitionagent.patch;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphOperationType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphProposalStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.support.GraphContractCopier;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardResponse;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import com.whu.software.athena.cognitionagent.patch.contract.PatchAssemblyStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GraphPatchPipelineTest {

    @Autowired CognitionGraphWorkflow workflow;
    @Autowired GraphPatchGuardService guardService;

    @Test
    void assemblyIsDeterministicAndGuardReturnsAReadyCopy() {
        GraphUpdatePreparationRequest request = createRequest("stable");
        int originalNodes = request.graph.nodes.size();
        GraphUpdatePreparationResponse first = workflow.prepare(request);
        GraphUpdatePreparationResponse second = workflow.prepare(request);

        assertEquals(GraphPreparationStatus.PROPOSAL_READY, first.status);
        assertEquals(PatchAssemblyStatus.ASSEMBLED, first.patchAssemblyResult.status);
        assertEquals(GraphProposalStatus.DRAFT, first.patchAssemblyResult.proposal.status);
        assertEquals(GraphProposalStatus.READY_FOR_CONFIRMATION, first.proposal.status);
        assertEquals(first.proposal.proposalId, second.proposal.proposalId);
        assertEquals(first.proposal.operations.stream().map(operation -> operation.operationType).toList(),
                second.proposal.operations.stream().map(operation -> operation.operationType).toList());
        assertEquals(originalNodes, request.graph.nodes.size());
        assertEquals(0, request.graph.graphVersion);
        assertTrue(first.proposal.operations.stream().anyMatch(operation ->
                operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.ACTION));
        assertTrue(first.proposal.operations.stream().anyMatch(operation ->
                operation.operationType == GraphOperationType.ADD_EDGE));
        int firstEdge = java.util.stream.IntStream.range(0, first.proposal.operations.size())
                .filter(index -> first.proposal.operations.get(index).operationType
                        == GraphOperationType.ADD_EDGE)
                .findFirst().orElseThrow();
        int lastNode = java.util.stream.IntStream.range(0, first.proposal.operations.size())
                .filter(index -> first.proposal.operations.get(index).operationType
                        == GraphOperationType.ADD_NODE
                        || first.proposal.operations.get(index).operationType
                        == GraphOperationType.UPDATE_NODE)
                .max().orElseThrow();
        assertTrue(lastNode < firstEdge, "all node operations must precede edge operations");
    }

    @Test
    void guardMarksStaleProposalWithoutApplyingIt() {
        GraphUpdatePreparationRequest workflowRequest = createRequest("stale");
        GraphUpdatePreparationResponse prepared = workflow.prepare(workflowRequest);
        GraphPatchGuardRequest guardRequest = guardRequest(workflowRequest, prepared);
        guardRequest.proposal = new GraphContractCopier().proposal(
                prepared.patchAssemblyResult.proposal);
        guardRequest.proposal.baseGraphVersion = 99;

        GraphPatchGuardResponse response = guardService.guard(guardRequest);

        assertEquals(PatchGuardStatus.STALE, response.status);
        assertEquals(GraphProposalStatus.STALE, response.proposal.status);
        assertEquals(0, workflowRequest.graph.graphVersion);
    }

    @Test
    void guardBlocksArticleEvidenceFromBecomingBodyFact() {
        GraphUpdatePreparationRequest workflowRequest = createRequest("fact-block");
        GraphUpdatePreparationResponse prepared = workflow.prepare(workflowRequest);
        GraphPatchGuardRequest guardRequest = guardRequest(workflowRequest, prepared);
        guardRequest.proposal = new GraphContractCopier().proposal(
                prepared.patchAssemblyResult.proposal);
        GraphNode semantic = guardRequest.proposal.operations.stream()
                .filter(operation -> operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.OPEN_QUESTION)
                .findFirst().orElseThrow().node;
        semantic.type = GraphNodeType.SELF_REPORTED_FACT;
        semantic.content = "The user has this condition.";

        GraphPatchGuardResponse response = guardService.guard(guardRequest);

        assertEquals(PatchGuardStatus.BLOCKED, response.status);
        assertEquals(GraphProposalStatus.BLOCKED, response.proposal.status);
        assertNotEquals(GraphProposalStatus.READY_FOR_CONFIRMATION, response.proposal.status);
    }

    @Test
    void guardBlocksEvidenceHiddenInsideANode() {
        GraphUpdatePreparationRequest workflowRequest = createRequest("hidden-node-evidence");
        GraphUpdatePreparationResponse prepared = workflow.prepare(workflowRequest);
        GraphPatchGuardRequest guardRequest = guardRequest(workflowRequest, prepared);
        guardRequest.proposal = new GraphContractCopier().proposal(
                prepared.patchAssemblyResult.proposal);
        GraphNode semantic = guardRequest.proposal.operations.stream()
                .filter(operation -> operation.operationType == GraphOperationType.ADD_NODE
                        && operation.node.type == GraphNodeType.OPEN_QUESTION)
                .findFirst().orElseThrow().node;
        semantic.evidenceIds = new java.util.ArrayList<>(semantic.evidenceIds);
        semantic.evidenceIds.add("evidence_not_selected");

        GraphPatchGuardResponse response = guardService.guard(guardRequest);

        assertEquals(PatchGuardStatus.BLOCKED, response.status);
        assertEquals(GraphProposalStatus.BLOCKED, response.proposal.status);
    }

    @Test
    void guardBlocksEvidenceHiddenInsideAnEdge() {
        GraphUpdatePreparationRequest workflowRequest = createRequest("hidden-edge-evidence");
        GraphUpdatePreparationResponse prepared = workflow.prepare(workflowRequest);
        GraphPatchGuardRequest guardRequest = guardRequest(workflowRequest, prepared);
        guardRequest.proposal = new GraphContractCopier().proposal(
                prepared.patchAssemblyResult.proposal);
        var edgeOperation = guardRequest.proposal.operations.stream()
                .filter(operation -> operation.operationType == GraphOperationType.ADD_EDGE)
                .findFirst().orElseThrow();
        edgeOperation.edge.evidenceIds = new java.util.ArrayList<>(
                edgeOperation.edge.evidenceIds);
        edgeOperation.edge.evidenceIds.add("evidence_not_selected");

        GraphPatchGuardResponse response = guardService.guard(guardRequest);

        assertEquals(PatchGuardStatus.BLOCKED, response.status);
        assertEquals(GraphProposalStatus.BLOCKED, response.proposal.status);
    }

    private GraphPatchGuardRequest guardRequest(GraphUpdatePreparationRequest request,
                                                GraphUpdatePreparationResponse response) {
        GraphPatchGuardRequest value = new GraphPatchGuardRequest();
        value.runId = request.runId + "_guard";
        value.idempotencyKey = request.idempotencyKey + ":guard-test";
        value.triggerType = request.triggerType;
        value.contextSnapshotId = request.contextSnapshotId;
        value.graph = request.graph;
        value.evidence = response.evidenceResult.acceptedEvidence;
        value.scope = response.scopeResult.scope;
        value.proposal = response.patchAssemblyResult.proposal;
        return value;
    }

    private GraphUpdatePreparationRequest createRequest(String suffix) {
        GraphUpdatePreparationRequest value = new GraphUpdatePreparationRequest();
        value.runId = "run_patch_" + suffix;
        value.idempotencyKey = "clue_patch_" + suffix + ":workflow";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_patch_" + suffix;
        value.graph = GraphTestFixtures.emptyGraph();
        value.candidates = List.of(GraphTestFixtures.relatedCandidate(
                "evidence_patch_" + suffix, "clue_patch_" + suffix));
        value.suggestedTopicTitle = "Cycle changes";
        value.requestedAt = "2026-08-27T12:00:00+08:00";
        return value;
    }
}
