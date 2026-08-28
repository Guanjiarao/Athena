package com.whu.software.athena.cognitionagent.semantic.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContext;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContextBuilder;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateResponse;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateStatus;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import com.whu.software.athena.cognitionagent.semantic.policy.GraphSemanticPolicyValidator;
import com.whu.software.athena.cognitionagent.semantic.provider.GraphSemanticModelProvider;
import com.whu.software.athena.cognitionagent.semantic.provider.SemanticModelSuggestion;
import com.whu.software.athena.cognitionagent.semantic.validation.GraphSemanticRequestValidator;
import com.whu.software.athena.cognitionagent.scope.policy.GraphUpdateScopePolicyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GraphSemanticUpdateService {

    private final GraphSemanticRequestValidator validator;
    private final GraphSemanticContextBuilder contextBuilder;
    private final GraphSemanticModelProvider modelProvider;
    private final GraphSemanticPolicyValidator policyValidator;
    private final GraphUpdateScopePolicyValidator scopePolicyValidator =
            new GraphUpdateScopePolicyValidator();
    private final WorkflowTelemetryRecorder telemetry;

    @Autowired
    public GraphSemanticUpdateService(GraphSemanticModelProvider modelProvider,
                                      WorkflowTelemetryRecorder telemetry) {
        this(new GraphSemanticRequestValidator(), new GraphSemanticContextBuilder(),
                modelProvider, new GraphSemanticPolicyValidator(), telemetry);
    }

    GraphSemanticUpdateService(GraphSemanticRequestValidator validator,
                               GraphSemanticContextBuilder contextBuilder,
                               GraphSemanticModelProvider modelProvider,
                               GraphSemanticPolicyValidator policyValidator,
                               WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.contextBuilder = contextBuilder;
        this.modelProvider = modelProvider;
        this.policyValidator = policyValidator;
        this.telemetry = telemetry;
    }

    public GraphSemanticUpdateResponse generate(GraphSemanticUpdateRequest request) {
        long startedAt = System.nanoTime();
        GraphSemanticUpdateResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = GraphSemanticUpdateStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    issue.code(), issue.message(), false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));
        PolicyValidationResult scopePolicy =
                scopePolicyValidator.validate(request.graph, request.scope);
        if (!scopePolicy.allowed()) {
            response.status = GraphSemanticUpdateStatus.BLOCKED;
            response.schemaResult = SchemaResult.PASS;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(scopePolicy.errorCode(), scopePolicy.message(),
                    false, scopePolicy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "SCOPE_POLICY", "recomputedFromGraph=true", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_POLICY", "recomputedFromGraph=true", "PASS"));
        GraphSemanticContext context = contextBuilder.build(request);
        observation.steps.add(new WorkflowNodeStep(
                "MODEL_CONTEXT",
                "graphNodeCount=" + context.graph().nodes.size()
                        + ",scopeReadableCount=" + context.scope().readableNodeIds.size(),
                "modelNodeCount=" + context.readableNodes().size()
                        + ",modelEvidenceCount=" + context.selectedEvidence().size()));

        try {
            SemanticModelSuggestion suggestion =
                    modelProvider.generate(contextBuilder.buildModelContext(context));
            response.schemaResult = SchemaResult.PASS;
            observation.schemaResult = SchemaResult.PASS;
            observation.modelCallStatus = ModelCallStatus.SUCCEEDED;
            copyUsage(suggestion, observation);

            PolicyValidationResult policy =
                    policyValidator.validate(context, suggestion.draft());
            response.policyResult = policy.allowed() ? PolicyResult.PASS : PolicyResult.BLOCK;
            observation.modelPolicyResult = response.policyResult;
            if (!policy.allowed()) {
                // integration debugging: surface the exact policy block reason in the log
                org.slf4j.LoggerFactory.getLogger(GraphSemanticUpdateService.class)
                        .warn("semantic policy blocked: field={} message={}",
                                policy.field(), policy.message());
                response.status = GraphSemanticUpdateStatus.BLOCKED;
                response.error = new AgentError(policy.errorCode(), policy.message(),
                        false, policy.field(), null);
                observation.modelCallStatus = ModelCallStatus.REJECTED;
                observation.modelErrorCode = AgentErrorCode.POLICY_BLOCKED.name();
                observation.steps.add(new WorkflowNodeStep(
                        "SEMANTIC_POLICY", "modelDraft", "BLOCK"));
                return finish(response, observation, startedAt);
            }
            response.draft = suggestion.draft();
            response.status = response.draft.changes.isEmpty()
                    || response.draft.changes.stream()
                    .allMatch(change -> change.changeType == SemanticChangeType.NO_CHANGE)
                    ? GraphSemanticUpdateStatus.NO_CHANGE
                    : GraphSemanticUpdateStatus.SUCCEEDED;
            observation.steps.add(new WorkflowNodeStep(
                    "MODEL_GENERATION",
                    "provider=" + suggestion.provider() + ",model=" + suggestion.modelName(),
                    "changeCount=" + response.draft.changes.size()));
            observation.steps.add(new WorkflowNodeStep(
                    "SEMANTIC_POLICY", "modelDraft", "PASS"));
        } catch (IntentModelProviderException exception) {
            response.status = GraphSemanticUpdateStatus.FAILED;
            response.schemaResult = exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                    ? SchemaResult.FAIL : SchemaResult.NOT_RUN;
            response.policyResult = PolicyResult.PASS;
            response.error = new AgentError(exception.errorCode(), exception.getMessage(),
                    exception.retryable(), null, null);
            observation.modelCallStatus =
                    exception.errorCode() == AgentErrorCode.MODEL_OUTPUT_INVALID
                            ? ModelCallStatus.REJECTED : ModelCallStatus.FAILED;
            observation.modelErrorCode = exception.errorCode().name();
            observation.steps.add(new WorkflowNodeStep(
                    "MODEL_GENERATION", "provider=" + modelProvider.providerName(),
                    "FAILED:" + exception.errorCode()));
        } catch (RuntimeException exception) {
            response.status = GraphSemanticUpdateStatus.FAILED;
            response.schemaResult = SchemaResult.NOT_RUN;
            response.policyResult = PolicyResult.PASS;
            response.error = new AgentError(AgentErrorCode.INTERNAL_ERROR,
                    "semantic update generation failed", true, null, null);
            observation.modelCallStatus = ModelCallStatus.FAILED;
            observation.modelErrorCode = AgentErrorCode.INTERNAL_ERROR.name();
        }
        return finish(response, observation, startedAt);
    }

    private GraphSemanticUpdateResponse baseResponse(GraphSemanticUpdateRequest request) {
        GraphSemanticUpdateResponse response = new GraphSemanticUpdateResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(GraphSemanticUpdateRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.SEMANTIC_NODE_ID;
        value.nodeVersion = GraphContract.SEMANTIC_NODE_VERSION;
        value.promptVersion = GraphContract.SEMANTIC_PROMPT_VERSION;
        value.modelProvider = modelProvider.providerName();
        value.modelName = modelProvider.modelName();
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private GraphSemanticUpdateResponse finish(GraphSemanticUpdateResponse response,
                                               WorkflowRunObservation observation,
                                               long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.draft != null) {
            observation.evidenceIds = response.draft.changes.stream()
                    .flatMap(change -> change.evidenceIds.stream()).distinct().toList();
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private void copyUsage(SemanticModelSuggestion suggestion,
                           WorkflowRunObservation observation) {
        observation.inputTokens = suggestion.inputTokens();
        observation.outputTokens = suggestion.outputTokens();
        observation.totalTokens = suggestion.totalTokens();
        observation.estimatedCost = suggestion.estimatedCost();
    }

    private String inputSummary(GraphSemanticUpdateRequest request) {
        int graphNodes = request == null || request.graph == null
                || request.graph.nodes == null ? 0 : request.graph.nodes.size();
        int evidence = request == null || request.evidence == null ? 0 : request.evidence.size();
        return "graphNodeCount=" + graphNodes + ",evidenceCount=" + evidence
                + ",scopePresent=" + (request != null && request.scope != null);
    }
}
