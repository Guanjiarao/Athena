package com.whu.software.athena.cognitionagent.workflow.service;

import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningStatus;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.action.service.NextActionPlanningService;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationStatus;
import com.whu.software.athena.cognitionagent.evidence.service.EvidenceCanonicalizationService;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyRequest;
import com.whu.software.athena.cognitionagent.patch.contract.PatchAssemblyStatus;
import com.whu.software.athena.cognitionagent.patch.service.GraphPatchAssemblyService;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeStatus;
import com.whu.software.athena.cognitionagent.scope.service.GraphUpdateScopePlanningService;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateStatus;
import com.whu.software.athena.cognitionagent.semantic.service.GraphSemanticUpdateService;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import com.whu.software.athena.cognitionagent.target.contract.TargetResolutionStatus;
import com.whu.software.athena.cognitionagent.target.service.GraphTargetResolutionService;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import org.springframework.stereotype.Service;

/**
 * Synchronous orchestration for nodes 2-8. It prepares a proposal and preview,
 * then stops at human confirmation without mutating the supplied graph.
 */
@Service
public class CognitionGraphWorkflow {

    private static final String WORKFLOW_ID = "COGNITION_GRAPH_WORKFLOW";

    private final EvidenceCanonicalizationService evidenceService;
    private final GraphTargetResolutionService targetService;
    private final GraphUpdateScopePlanningService scopeService;
    private final GraphSemanticUpdateService semanticService;
    private final NextActionPlanningService actionService;
    private final GraphPatchAssemblyService patchAssemblyService;
    private final GraphPatchGuardService patchGuardService;
    private final WorkflowTelemetryRecorder telemetry;
    private final GraphIntegrityValidator graphValidator = new GraphIntegrityValidator();

    public CognitionGraphWorkflow(EvidenceCanonicalizationService evidenceService,
                                  GraphTargetResolutionService targetService,
                                  GraphUpdateScopePlanningService scopeService,
                                  GraphSemanticUpdateService semanticService,
                                  NextActionPlanningService actionService,
                                  GraphPatchAssemblyService patchAssemblyService,
                                  GraphPatchGuardService patchGuardService,
                                  WorkflowTelemetryRecorder telemetry) {
        this.evidenceService = evidenceService;
        this.targetService = targetService;
        this.scopeService = scopeService;
        this.semanticService = semanticService;
        this.actionService = actionService;
        this.patchAssemblyService = patchAssemblyService;
        this.patchGuardService = patchGuardService;
        this.telemetry = telemetry;
    }

    public GraphUpdatePreparationResponse prepare(GraphUpdatePreparationRequest request) {
        long startedAt = System.nanoTime();
        GraphUpdatePreparationResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        try {
            AgentError requestError = validateRequest(request);
            if (requestError != null) {
                response.status = GraphPreparationStatus.REJECTED;
                response.error = requestError;
                observation.schemaResult = SchemaResult.FAIL;
                observation.policyResult = PolicyResult.BLOCK;
                observation.steps.add(new WorkflowNodeStep(
                        "WORKFLOW_INPUT", inputSummary(request), "REJECTED"));
                return response;
            }
            observation.schemaResult = SchemaResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "WORKFLOW_INPUT", inputSummary(request), "PASS"));

            EvidenceCanonicalizationRequest evidenceRequest =
                    new EvidenceCanonicalizationRequest();
            copyMetadata(request, evidenceRequest);
            evidenceRequest.candidates = request.candidates;
            evidenceRequest.existingEvidence = request.existingEvidence;
            response.evidenceResult = evidenceService.canonicalize(evidenceRequest);
            step(observation, GraphContract.EVIDENCE_NODE_ID,
                    response.evidenceResult.status);
            if (response.evidenceResult.status == EvidenceCanonicalizationStatus.NO_CHANGE) {
                response.status = GraphPreparationStatus.NO_CHANGE;
                return response;
            }
            if (response.evidenceResult.status != EvidenceCanonicalizationStatus.SUCCEEDED) {
                response.status = response.evidenceResult.status
                        == EvidenceCanonicalizationStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.evidenceResult.error;
                return response;
            }

            GraphTargetResolutionRequest targetRequest = new GraphTargetResolutionRequest();
            copyMetadata(request, targetRequest);
            targetRequest.graph = request.graph;
            targetRequest.evidence = response.evidenceResult.acceptedEvidence;
            targetRequest.userSelectedTopicId = request.userSelectedTopicId;
            targetRequest.suggestedTopicTitle = request.suggestedTopicTitle;
            response.targetResult = targetService.resolve(targetRequest);
            step(observation, GraphContract.TARGET_NODE_ID, response.targetResult.status);
            if (response.targetResult.status == TargetResolutionStatus.NO_CHANGE) {
                response.status = GraphPreparationStatus.NO_CHANGE;
                return response;
            }
            if (response.targetResult.status == TargetResolutionStatus.NEEDS_CONFIRMATION) {
                response.status = GraphPreparationStatus.NEEDS_CONFIRMATION;
                response.error = response.targetResult.error;
                return response;
            }
            if (response.targetResult.status != TargetResolutionStatus.SUCCEEDED) {
                response.status = response.targetResult.status == TargetResolutionStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.targetResult.error;
                return response;
            }

            GraphUpdateScopeRequest scopeRequest = new GraphUpdateScopeRequest();
            copyMetadata(request, scopeRequest);
            scopeRequest.graph = request.graph;
            scopeRequest.evidence = response.evidenceResult.acceptedEvidence;
            scopeRequest.targetRoute = response.targetResult.route;
            scopeRequest.targetTopicId = response.targetResult.targetTopicId;
            scopeRequest.proposedTopicTitle = response.targetResult.suggestedTopicTitle;
            response.scopeResult = scopeService.plan(scopeRequest);
            step(observation, GraphContract.SCOPE_NODE_ID, response.scopeResult.status);
            if (response.scopeResult.status == GraphUpdateScopeStatus.NO_CHANGE) {
                response.status = GraphPreparationStatus.NO_CHANGE;
                return response;
            }
            if (response.scopeResult.status != GraphUpdateScopeStatus.READY) {
                response.status = response.scopeResult.status == GraphUpdateScopeStatus.BLOCKED
                        ? GraphPreparationStatus.BLOCKED
                        : response.scopeResult.status == GraphUpdateScopeStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.scopeResult.error;
                return response;
            }

            GraphSemanticUpdateRequest semanticRequest = new GraphSemanticUpdateRequest();
            copyMetadata(request, semanticRequest);
            semanticRequest.graph = request.graph;
            semanticRequest.evidence = response.evidenceResult.acceptedEvidence;
            semanticRequest.scope = response.scopeResult.scope;
            response.semanticResult = semanticService.generate(semanticRequest);
            step(observation, GraphContract.SEMANTIC_NODE_ID, response.semanticResult.status);
            if (response.semanticResult.status == GraphSemanticUpdateStatus.NO_CHANGE) {
                response.status = GraphPreparationStatus.NO_CHANGE;
                return response;
            }
            if (response.semanticResult.status != GraphSemanticUpdateStatus.SUCCEEDED) {
                response.status = response.semanticResult.status == GraphSemanticUpdateStatus.BLOCKED
                        ? GraphPreparationStatus.BLOCKED
                        : response.semanticResult.status == GraphSemanticUpdateStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.semanticResult.error;
                return response;
            }

            NextActionPlanningRequest actionRequest = new NextActionPlanningRequest();
            copyMetadata(request, actionRequest);
            actionRequest.graph = request.graph;
            actionRequest.evidence = response.evidenceResult.acceptedEvidence;
            actionRequest.scope = response.scopeResult.scope;
            actionRequest.semanticDraft = response.semanticResult.draft;
            response.actionResult = actionService.plan(actionRequest);
            step(observation, GraphContract.ACTION_NODE_ID, response.actionResult.status);
            if (response.actionResult.status != ActionPlanningStatus.READY) {
                response.status = response.actionResult.status == ActionPlanningStatus.BLOCKED
                        ? GraphPreparationStatus.BLOCKED
                        : response.actionResult.status == ActionPlanningStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.actionResult.error;
                return response;
            }

            GraphPatchAssemblyRequest patchRequest = new GraphPatchAssemblyRequest();
            copyMetadata(request, patchRequest);
            patchRequest.graph = request.graph;
            patchRequest.evidence = response.evidenceResult.acceptedEvidence;
            patchRequest.scope = response.scopeResult.scope;
            patchRequest.semanticDraft = response.semanticResult.draft;
            patchRequest.actionPlan = response.actionResult.plan;
            patchRequest.proposalCreatedAt = request.requestedAt;
            response.patchAssemblyResult = patchAssemblyService.assemble(patchRequest);
            step(observation, GraphContract.PATCH_ASSEMBLY_NODE_ID,
                    response.patchAssemblyResult.status);
            if (response.patchAssemblyResult.status == PatchAssemblyStatus.NO_CHANGE) {
                response.status = GraphPreparationStatus.NO_CHANGE;
                return response;
            }
            if (response.patchAssemblyResult.status != PatchAssemblyStatus.ASSEMBLED) {
                response.status = response.patchAssemblyResult.status == PatchAssemblyStatus.REJECTED
                        ? GraphPreparationStatus.REJECTED : GraphPreparationStatus.FAILED;
                response.error = response.patchAssemblyResult.error;
                return response;
            }

            GraphPatchGuardRequest guardRequest = new GraphPatchGuardRequest();
            copyMetadata(request, guardRequest);
            guardRequest.graph = request.graph;
            guardRequest.evidence = response.evidenceResult.acceptedEvidence;
            guardRequest.scope = response.scopeResult.scope;
            guardRequest.proposal = response.patchAssemblyResult.proposal;
            response.patchGuardResult = patchGuardService.guard(guardRequest);
            step(observation, GraphContract.PATCH_GUARD_NODE_ID,
                    response.patchGuardResult.status);
            if (response.patchGuardResult.status != PatchGuardStatus.READY_FOR_CONFIRMATION) {
                response.status = switch (response.patchGuardResult.status) {
                    case STALE -> GraphPreparationStatus.STALE;
                    case BLOCKED -> GraphPreparationStatus.BLOCKED;
                    case REJECTED -> GraphPreparationStatus.REJECTED;
                    default -> GraphPreparationStatus.FAILED;
                };
                response.error = response.patchGuardResult.error;
                return response;
            }
            response.proposal = response.patchGuardResult.proposal;
            response.graphPreview = response.patchGuardResult.graphPreview;
            response.status = GraphPreparationStatus.PROPOSAL_READY;
            response.nextNodeId = "HUMAN_CONFIRMATION";
            observation.policyResult = PolicyResult.PASS;
            return response;
        } catch (RuntimeException exception) {
            response.status = GraphPreparationStatus.FAILED;
            response.error = new AgentError(AgentErrorCode.INTERNAL_ERROR,
                    "cognition graph workflow failed", true, null, null);
            return response;
        } finally {
            finish(response, observation, startedAt);
        }
    }

    private AgentError validateRequest(GraphUpdatePreparationRequest request) {
        if (request == null) {
            return error(AgentErrorCode.INVALID_REQUEST, "request is required", "request");
        }
        if (!GraphContract.CONTRACT_VERSION.equals(request.contractVersion)
                || !GraphContract.WORKFLOW_VERSION.equals(request.workflowVersion)) {
            return error(AgentErrorCode.UNSUPPORTED_VERSION,
                    "unsupported contract or workflow version", "workflowVersion");
        }
        if (blank(request.runId) || blank(request.idempotencyKey)
                || blank(request.contextSnapshotId)) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "runId, idempotencyKey and contextSnapshotId are required", null);
        }
        if (request.triggerType == null) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "triggerType is required", "triggerType");
        }
        if (request.triggerType == GraphTriggerType.ACTION_FEEDBACK) {
            return error(AgentErrorCode.INVALID_REQUEST,
                    "ACTION_FEEDBACK must use the action feedback workflow", "triggerType");
        }
        String graphError = graphValidator.validate(request.graph);
        if (graphError != null) {
            return error(AgentErrorCode.INVALID_REQUEST, graphError, "graph");
        }
        if (request.candidates == null || request.candidates.isEmpty()) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "at least one evidence candidate is required", "candidates");
        }
        if (request.existingEvidence == null) {
            return error(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "existingEvidence is required", "existingEvidence");
        }
        return null;
    }

    private void finish(GraphUpdatePreparationResponse response,
                        WorkflowRunObservation observation,
                        long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        if (response.status == GraphPreparationStatus.BLOCKED
                || response.status == GraphPreparationStatus.REJECTED
                || response.status == GraphPreparationStatus.STALE) {
            observation.policyResult = PolicyResult.BLOCK;
        } else if (observation.policyResult == null && response.status != null) {
            observation.policyResult = PolicyResult.PASS;
        }
        if (response.proposal != null) {
            observation.evidenceIds = response.proposal.evidenceIds;
            observation.operationCount = response.proposal.operations.size();
            observation.baseGraphVersion = response.proposal.baseGraphVersion;
        } else if (response.evidenceResult != null) {
            observation.evidenceIds = response.evidenceResult.acceptedEvidence.stream()
                    .map(item -> item.evidenceId).toList();
        }
        if (response.graphPreview != null) {
            observation.previewGraphVersion = response.graphPreview.graphVersion;
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.recordWorkflow(GraphContract.WORKFLOW_VERSION,
                response.status == null ? "FAILED" : response.status.name(), observation);
    }

    private WorkflowRunObservation observation(GraphUpdatePreparationRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = WORKFLOW_ID;
        value.nodeVersion = GraphContract.WORKFLOW_VERSION;
        value.modelProvider = "orchestrator";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        value.baseGraphVersion = request == null || request.graph == null
                ? null : request.graph.graphVersion;
        return value;
    }

    private void step(WorkflowRunObservation observation, String nodeId, Object status) {
        observation.steps.add(new WorkflowNodeStep(
                nodeId, "nodeCompleted=true", "status=" + status));
    }

    private String inputSummary(GraphUpdatePreparationRequest request) {
        int candidateCount = request == null || request.candidates == null
                ? 0 : request.candidates.size();
        return "triggerType=" + (request == null ? null : request.triggerType)
                + ",candidateCount=" + candidateCount;
    }

    private AgentError error(AgentErrorCode code, String message, String field) {
        return new AgentError(code, message, false, field, null);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private GraphUpdatePreparationResponse baseResponse(GraphUpdatePreparationRequest request) {
        GraphUpdatePreparationResponse response = new GraphUpdatePreparationResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              EvidenceCanonicalizationRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node2";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              GraphTargetResolutionRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node3";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              GraphUpdateScopeRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node4";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              GraphSemanticUpdateRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node5";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              NextActionPlanningRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node6";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              GraphPatchAssemblyRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node7";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }

    private void copyMetadata(GraphUpdatePreparationRequest source,
                              GraphPatchGuardRequest target) {
        target.runId = source.runId;
        target.idempotencyKey = source.idempotencyKey + ":node8";
        target.triggerType = source.triggerType;
        target.contextSnapshotId = source.contextSnapshotId;
    }
}
