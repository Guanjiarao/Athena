package com.whu.software.athena.cognitionagent.guard.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphProposalStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.support.GraphContractCopier;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardResponse;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.policy.GraphPatchPolicyValidator;
import com.whu.software.athena.cognitionagent.guard.validation.GraphPatchGuardRequestValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.scope.policy.GraphUpdateScopePolicyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GraphPatchGuardService {

    private final GraphPatchGuardRequestValidator validator;
    private final GraphPatchPolicyValidator policyValidator;
    private final GraphPatchSimulationService simulation;
    private final GraphContractCopier copier;
    private final WorkflowTelemetryRecorder telemetry;
    private final GraphUpdateScopePolicyValidator scopePolicyValidator =
            new GraphUpdateScopePolicyValidator();

    @Autowired
    public GraphPatchGuardService(WorkflowTelemetryRecorder telemetry) {
        this(new GraphPatchGuardRequestValidator(), new GraphPatchPolicyValidator(),
                new GraphPatchSimulationService(), new GraphContractCopier(), telemetry);
    }

    GraphPatchGuardService(GraphPatchGuardRequestValidator validator,
                           GraphPatchPolicyValidator policyValidator,
                           GraphPatchSimulationService simulation,
                           GraphContractCopier copier,
                           WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.policyValidator = policyValidator;
        this.simulation = simulation;
        this.copier = copier;
        this.telemetry = telemetry;
    }

    public GraphPatchGuardResponse guard(GraphPatchGuardRequest request) {
        long startedAt = System.nanoTime();
        GraphPatchGuardResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = PatchGuardStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    issue.code(), issue.message(), false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "PROPOSAL_SCHEMA", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "PROPOSAL_SCHEMA", inputSummary(request), "PASS"));

        boolean feedbackRun = request.triggerType == GraphTriggerType.ACTION_FEEDBACK;
        PolicyValidationResult scopePolicy = scopePolicyValidator.validate(
                request.graph, request.scope, feedbackRun);
        if (!scopePolicy.allowed()) {
            response.status = PatchGuardStatus.BLOCKED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(scopePolicy.errorCode(), scopePolicy.message(),
                    false, scopePolicy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "SCOPE_POLICY", "recomputedFromGraph=true", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_POLICY", "recomputedFromGraph=true", "PASS"));

        if (request.proposal.baseGraphVersion != request.graph.graphVersion
                || request.scope.baseGraphVersion != request.graph.graphVersion) {
            response.status = PatchGuardStatus.STALE;
            response.policyResult = PolicyResult.BLOCK;
            response.proposal = copier.proposal(request.proposal);
            response.proposal.status = GraphProposalStatus.STALE;
            response.error = new AgentError(AgentErrorCode.GRAPH_VERSION_CONFLICT,
                    "proposal base graph version is stale", false,
                    "proposal.baseGraphVersion", null);
            observation.steps.add(new WorkflowNodeStep(
                    "VERSION_GUARD",
                    "graphVersion=" + request.graph.graphVersion,
                    "proposalVersion=" + request.proposal.baseGraphVersion + ":STALE"));
            return finish(response, observation, startedAt);
        }

        PolicyValidationResult policy = policyValidator.validate(
                request.graph, request.evidence, request.scope, request.proposal);
        response.policyResult = policy.allowed() ? PolicyResult.PASS : PolicyResult.BLOCK;
        if (!policy.allowed()) {
            response.status = PatchGuardStatus.BLOCKED;
            response.proposal = copier.proposal(request.proposal);
            response.proposal.status = GraphProposalStatus.BLOCKED;
            response.error = new AgentError(policy.errorCode(), policy.message(),
                    false, policy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "PATCH_POLICY", "proposalId=" + request.proposal.proposalId, "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "PATCH_POLICY", "proposalId=" + request.proposal.proposalId, "PASS"));

        GraphPatchSimulationService.SimulationResult simulated =
                simulation.simulate(request.graph, request.proposal);
        if (!simulated.valid()) {
            response.status = PatchGuardStatus.BLOCKED;
            response.policyResult = PolicyResult.BLOCK;
            response.proposal = copier.proposal(request.proposal);
            response.proposal.status = GraphProposalStatus.BLOCKED;
            response.error = new AgentError(AgentErrorCode.GRAPH_INTEGRITY_VIOLATION,
                    simulated.error(), false, "proposal.operations", null);
            observation.steps.add(new WorkflowNodeStep(
                    "PATCH_SIMULATION", "operationCount=" + request.proposal.operations.size(),
                    "BLOCK"));
            return finish(response, observation, startedAt);
        }
        response.proposal = copier.proposal(request.proposal);
        response.proposal.status = GraphProposalStatus.READY_FOR_CONFIRMATION;
        response.graphPreview = simulated.simulatedGraph();
        response.status = PatchGuardStatus.READY_FOR_CONFIRMATION;
        observation.steps.add(new WorkflowNodeStep(
                "PATCH_SIMULATION", "operationCount=" + request.proposal.operations.size(),
                "simulatedGraphVersion=" + simulated.simulatedGraph().graphVersion));
        return finish(response, observation, startedAt);
    }

    private GraphPatchGuardResponse baseResponse(GraphPatchGuardRequest request) {
        GraphPatchGuardResponse response = new GraphPatchGuardResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(GraphPatchGuardRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = request != null
                && request.triggerType == GraphTriggerType.ACTION_FEEDBACK
                ? GraphContract.FEEDBACK_WORKFLOW_VERSION : GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.PATCH_GUARD_NODE_ID;
        value.nodeVersion = GraphContract.PATCH_GUARD_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private GraphPatchGuardResponse finish(GraphPatchGuardResponse response,
                                           WorkflowRunObservation observation,
                                           long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.proposal != null) observation.evidenceIds = response.proposal.evidenceIds;
        if (response.proposal != null) {
            observation.operationCount = response.proposal.operations.size();
            observation.baseGraphVersion = response.proposal.baseGraphVersion;
        }
        if (response.graphPreview != null) {
            observation.previewGraphVersion = response.graphPreview.graphVersion;
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private String inputSummary(GraphPatchGuardRequest request) {
        int operations = request == null || request.proposal == null
                || request.proposal.operations == null ? 0 : request.proposal.operations.size();
        return "operationCount=" + operations + ",proposalPresent="
                + (request != null && request.proposal != null);
    }
}
