package com.whu.software.athena.cognitionagent.action.service;

import com.whu.software.athena.cognitionagent.action.context.NextActionContext;
import com.whu.software.athena.cognitionagent.action.context.NextActionContextBuilder;
import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningDecision;
import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningStatus;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlan;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningResponse;
import com.whu.software.athena.cognitionagent.action.policy.NextActionPolicyValidator;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelOutput;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelProvider;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelSuggestion;
import com.whu.software.athena.cognitionagent.action.validation.NextActionRequestValidator;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.scope.policy.GraphUpdateScopePolicyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NextActionPlanningService {

    private final NextActionRequestValidator validator;
    private final NextActionContextBuilder contextBuilder;
    private final NextActionModelProvider modelProvider;
    private final NextActionPolicyValidator policyValidator;
    private final GraphUpdateScopePolicyValidator scopePolicyValidator =
            new GraphUpdateScopePolicyValidator();
    private final WorkflowTelemetryRecorder telemetry;

    @Autowired
    public NextActionPlanningService(NextActionModelProvider modelProvider,
                                     WorkflowTelemetryRecorder telemetry) {
        this(new NextActionRequestValidator(), new NextActionContextBuilder(),
                modelProvider, new NextActionPolicyValidator(), telemetry);
    }

    NextActionPlanningService(NextActionRequestValidator validator,
                              NextActionContextBuilder contextBuilder,
                              NextActionModelProvider modelProvider,
                              NextActionPolicyValidator policyValidator,
                              WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.contextBuilder = contextBuilder;
        this.modelProvider = modelProvider;
        this.policyValidator = policyValidator;
        this.telemetry = telemetry;
    }

    public NextActionPlanningResponse plan(NextActionPlanningRequest request) {
        long startedAt = System.nanoTime();
        NextActionPlanningResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = ActionPlanningStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    issue.code(), issue.message(), false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));
        PolicyValidationResult scopePolicy =
                scopePolicyValidator.validate(request.graph, request.scope);
        if (!scopePolicy.allowed()) {
            response.status = ActionPlanningStatus.BLOCKED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(scopePolicy.errorCode(), scopePolicy.message(),
                    false, scopePolicy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "SCOPE_POLICY", "recomputedFromGraph=true", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_POLICY", "recomputedFromGraph=true", "PASS"));
        NextActionContext context = contextBuilder.build(request);

        if (context.existingPendingAction() != null) {
            response.plan = retainedPlan(context.existingPendingAction(),
                    request.scope.selectedEvidenceIds);
            observation.steps.add(new WorkflowNodeStep(
                    "DETERMINISTIC_REUSE", "pendingActionPresent=true",
                    "actionNodeId=" + response.plan.existingActionNodeId));
        } else {
            observation.steps.add(new WorkflowNodeStep(
                    "MODEL_CONTEXT", "fullGraphNodeCount=" + request.graph.nodes.size(),
                    "modelEvidenceCount=" + context.selectedEvidence().size()));
            try {
                NextActionModelSuggestion suggestion = modelProvider.plan(
                        contextBuilder.buildModelContext(context));
                response.plan = newPlan(suggestion.output());
                observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
                observation.schemaResult = SchemaResult.PASS;
                copyUsage(suggestion, observation);
                observation.steps.add(new WorkflowNodeStep(
                        "MODEL_GENERATION",
                        "provider=" + suggestion.provider() + ",model=" + suggestion.modelName(),
                        "actionType=" + response.plan.actionType));
            } catch (IntentModelProviderException exception) {
                response.status = ActionPlanningStatus.FAILED;
                response.schemaResult = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                        ? SchemaResult.FAIL : SchemaResult.NOT_RUN;
                response.policyResult = PolicyResult.PASS;
                response.error = new AgentError(exception.errorCode(), exception.getMessage(),
                        exception.retryable(), null, null);
                observation.modelCallStatus =
                        exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                                ? ModelCallStatus.REJECTED : ModelCallStatus.FAILED;
                observation.modelErrorCode = exception.errorCode().name();
                return finish(response, observation, startedAt);
            } catch (RuntimeException exception) {
                response.status = ActionPlanningStatus.FAILED;
                response.schemaResult = SchemaResult.NOT_RUN;
                response.policyResult = PolicyResult.PASS;
                response.error = new AgentError(AgentErrorCode.INTERNAL_ERROR,
                        "next action planning failed", true, null, null);
                observation.modelCallStatus = ModelCallStatus.FAILED;
                observation.modelErrorCode = AgentErrorCode.INTERNAL_ERROR.name();
                return finish(response, observation, startedAt);
            }
        }

        PolicyValidationResult policy = policyValidator.validate(context, response.plan);
        response.policyResult = policy.allowed() ? PolicyResult.PASS : PolicyResult.BLOCK;
        observation.modelPolicyResult = context.existingPendingAction() == null
                ? response.policyResult : null;
        if (!policy.allowed()) {
            response.status = ActionPlanningStatus.BLOCKED;
            response.error = new AgentError(policy.errorCode(), policy.message(),
                    false, policy.field(), null);
            observation.modelCallStatus = context.existingPendingAction() == null
                    ? ModelCallStatus.REJECTED : ModelCallStatus.NOT_ATTEMPTED;
            observation.modelErrorCode = AgentErrorCode.POLICY_BLOCKED.name();
            return finish(response, observation, startedAt);
        }
        response.status = ActionPlanningStatus.READY;
        observation.steps.add(new WorkflowNodeStep(
                "ACTION_POLICY", "plannedAction", "PASS"));
        return finish(response, observation, startedAt);
    }

    private NextActionPlan retainedPlan(GraphNode node, List<String> currentEvidenceIds) {
        NextActionPlan plan = new NextActionPlan();
        plan.decision = ActionPlanningDecision.KEEP_EXISTING;
        plan.existingActionNodeId = node.id;
        plan.actionType = node.actionType;
        plan.title = node.title;
        plan.description = node.content;
        plan.dueAt = node.dueAt;
        plan.feedbackOptions = List.copyOf(node.feedbackOptions);
        plan.evidenceIds = List.copyOf(currentEvidenceIds);
        plan.rationale = "An active pending action already exists for this topic.";
        return plan;
    }

    private NextActionPlan newPlan(NextActionModelOutput output) {
        NextActionPlan plan = new NextActionPlan();
        plan.decision = ActionPlanningDecision.CREATE_NEW;
        plan.actionType = output.actionType;
        plan.title = output.title;
        plan.description = output.description;
        plan.feedbackOptions = policyValidator.feedbackOptions();
        plan.evidenceIds = List.copyOf(output.evidenceIds);
        plan.rationale = output.rationale;
        return plan;
    }

    private NextActionPlanningResponse baseResponse(NextActionPlanningRequest request) {
        NextActionPlanningResponse response = new NextActionPlanningResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(NextActionPlanningRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.ACTION_NODE_ID;
        value.nodeVersion = GraphContract.ACTION_NODE_VERSION;
        value.promptVersion = GraphContract.ACTION_PROMPT_VERSION;
        value.modelProvider = modelProvider.providerName();
        value.modelName = modelProvider.modelName();
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private NextActionPlanningResponse finish(NextActionPlanningResponse response,
                                              WorkflowRunObservation observation,
                                              long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.plan != null) observation.evidenceIds = response.plan.evidenceIds;
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private void copyUsage(NextActionModelSuggestion suggestion,
                           WorkflowRunObservation observation) {
        observation.inputTokens = suggestion.inputTokens();
        observation.outputTokens = suggestion.outputTokens();
        observation.totalTokens = suggestion.totalTokens();
        observation.estimatedCost = suggestion.estimatedCost();
    }

    private String inputSummary(NextActionPlanningRequest request) {
        int nodes = request == null || request.graph == null || request.graph.nodes == null
                ? 0 : request.graph.nodes.size();
        int evidence = request == null || request.evidence == null ? 0 : request.evidence.size();
        return "graphNodeCount=" + nodes + ",evidenceCount=" + evidence
                + ",scopePresent=" + (request != null && request.scope != null);
    }
}
