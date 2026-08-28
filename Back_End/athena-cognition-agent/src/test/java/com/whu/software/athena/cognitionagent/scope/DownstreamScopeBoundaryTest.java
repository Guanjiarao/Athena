package com.whu.software.athena.cognitionagent.scope;

import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningStatus;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.action.service.NextActionPlanningService;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyRequest;
import com.whu.software.athena.cognitionagent.patch.contract.PatchAssemblyStatus;
import com.whu.software.athena.cognitionagent.patch.service.GraphPatchAssemblyService;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateStatus;
import com.whu.software.athena.cognitionagent.semantic.service.GraphSemanticUpdateService;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DownstreamScopeBoundaryTest {

    @Autowired CognitionGraphWorkflow workflow;
    @Autowired GraphSemanticUpdateService semanticService;
    @Autowired NextActionPlanningService actionService;
    @Autowired GraphPatchAssemblyService patchService;
    @Autowired GraphPatchGuardService guardService;

    private GraphUpdatePreparationRequest workflowRequest;
    private GraphUpdatePreparationResponse prepared;

    @BeforeEach
    void prepareValidPipelineArtifacts() {
        workflowRequest = new GraphUpdatePreparationRequest();
        workflowRequest.runId = "run_scope_boundary";
        workflowRequest.idempotencyKey = "scope-boundary";
        workflowRequest.triggerType = GraphTriggerType.USER_REQUEST;
        workflowRequest.contextSnapshotId = "ctx_scope_boundary";
        workflowRequest.graph = GraphTestFixtures.graphWithTwoTopics();
        workflowRequest.candidates.add(GraphTestFixtures.relatedCandidate(
                "evidence_scope_boundary", "clue_scope_boundary"));
        workflowRequest.userSelectedTopicId = "topic_mood";
        prepared = workflow.prepare(workflowRequest);
    }

    @Test
    void nodeFiveRejectsCrossBranchScope() {
        GraphSemanticUpdateRequest request = new GraphSemanticUpdateRequest();
        metadata(request);
        request.graph = workflowRequest.graph;
        request.evidence = prepared.evidenceResult.acceptedEvidence;
        request.scope = forgedScope();

        assertEquals(GraphSemanticUpdateStatus.BLOCKED,
                semanticService.generate(request).status);
    }

    @Test
    void nodeSixRejectsCrossBranchScope() {
        NextActionPlanningRequest request = new NextActionPlanningRequest();
        metadata(request);
        request.graph = workflowRequest.graph;
        request.evidence = prepared.evidenceResult.acceptedEvidence;
        request.scope = forgedScope();
        request.semanticDraft = prepared.semanticResult.draft;

        assertEquals(ActionPlanningStatus.BLOCKED, actionService.plan(request).status);
    }

    @Test
    void nodeSevenRejectsCrossBranchScope() {
        GraphPatchAssemblyRequest request = new GraphPatchAssemblyRequest();
        metadata(request);
        request.graph = workflowRequest.graph;
        request.evidence = prepared.evidenceResult.acceptedEvidence;
        request.scope = forgedScope();
        request.semanticDraft = prepared.semanticResult.draft;
        request.actionPlan = prepared.actionResult.plan;

        assertEquals(PatchAssemblyStatus.REJECTED, patchService.assemble(request).status);
    }

    @Test
    void nodeEightRejectsCrossBranchScope() {
        GraphPatchGuardRequest request = new GraphPatchGuardRequest();
        metadata(request);
        request.graph = workflowRequest.graph;
        request.evidence = prepared.evidenceResult.acceptedEvidence;
        request.scope = forgedScope();
        request.proposal = prepared.patchAssemblyResult.proposal;

        assertEquals(PatchGuardStatus.BLOCKED, guardService.guard(request).status);
    }

    private GraphUpdateScope forgedScope() {
        GraphUpdateScope source = prepared.scopeResult.scope;
        GraphUpdateScope value = new GraphUpdateScope();
        value.graphId = source.graphId;
        value.baseGraphVersion = source.baseGraphVersion;
        value.route = source.route;
        value.targetTopicId = source.targetTopicId;
        value.proposedTopicTitle = source.proposedTopicTitle;
        value.selectedEvidenceIds = new ArrayList<>(source.selectedEvidenceIds);
        value.readableNodeIds = new ArrayList<>(source.readableNodeIds);
        value.mutableNodeIds = new ArrayList<>(source.mutableNodeIds);
        value.immutableNodeIds = new ArrayList<>(source.immutableNodeIds);
        value.readableNodeIds.add("topic_sleep");
        value.immutableNodeIds.add("topic_sleep");
        return value;
    }

    private void metadata(GraphSemanticUpdateRequest request) {
        request.runId = workflowRequest.runId;
        request.idempotencyKey = "scope-boundary:node5";
        request.triggerType = workflowRequest.triggerType;
        request.contextSnapshotId = workflowRequest.contextSnapshotId;
    }

    private void metadata(NextActionPlanningRequest request) {
        request.runId = workflowRequest.runId;
        request.idempotencyKey = "scope-boundary:node6";
        request.triggerType = workflowRequest.triggerType;
        request.contextSnapshotId = workflowRequest.contextSnapshotId;
    }

    private void metadata(GraphPatchAssemblyRequest request) {
        request.runId = workflowRequest.runId;
        request.idempotencyKey = "scope-boundary:node7";
        request.triggerType = workflowRequest.triggerType;
        request.contextSnapshotId = workflowRequest.contextSnapshotId;
    }

    private void metadata(GraphPatchGuardRequest request) {
        request.runId = workflowRequest.runId;
        request.idempotencyKey = "scope-boundary:node8";
        request.triggerType = workflowRequest.triggerType;
        request.contextSnapshotId = workflowRequest.contextSnapshotId;
    }
}
