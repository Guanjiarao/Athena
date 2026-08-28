package com.whu.software.athena.cognitionagent.scope.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.scope.context.GraphUpdateScopeContext;
import com.whu.software.athena.cognitionagent.scope.context.GraphUpdateScopeContextBuilder;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeResponse;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeStatus;
import com.whu.software.athena.cognitionagent.scope.policy.GraphUpdateScopePolicyValidator;
import com.whu.software.athena.cognitionagent.scope.validation.GraphUpdateScopeRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GraphUpdateScopePlanningService {

    private final GraphUpdateScopeRequestValidator validator;
    private final GraphUpdateScopeContextBuilder contextBuilder;
    private final GraphUpdateScopePolicyValidator policyValidator;
    private final WorkflowTelemetryRecorder telemetry;

    @Autowired
    public GraphUpdateScopePlanningService(WorkflowTelemetryRecorder telemetry) {
        this(new GraphUpdateScopeRequestValidator(), new GraphUpdateScopeContextBuilder(),
                new GraphUpdateScopePolicyValidator(), telemetry);
    }

    GraphUpdateScopePlanningService(GraphUpdateScopeRequestValidator validator,
                                    GraphUpdateScopeContextBuilder contextBuilder,
                                    GraphUpdateScopePolicyValidator policyValidator,
                                    WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.contextBuilder = contextBuilder;
        this.policyValidator = policyValidator;
        this.telemetry = telemetry;
    }

    public GraphUpdateScopeResponse plan(GraphUpdateScopeRequest request) {
        long startedAt = System.nanoTime();
        GraphUpdateScopeResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = GraphUpdateScopeStatus.REJECTED;
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
        GraphUpdateScopeContext context = contextBuilder.build(request);

        if (context.targetRoute() == GraphUpdateRoute.NO_CHANGE) {
            response.status = GraphUpdateScopeStatus.NO_CHANGE;
            response.policyResult = PolicyResult.PASS;
            observation.steps.add(new WorkflowNodeStep(
                    "ROUTE_GATE", "targetRoute=NO_CHANGE", "NO_CHANGE"));
            return finish(response, observation, startedAt);
        }
        if (context.targetRoute() == GraphUpdateRoute.NEEDS_CONFIRMATION) {
            response.status = GraphUpdateScopeStatus.BLOCKED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.POLICY_BLOCKED,
                    "target branch requires user confirmation before semantic generation",
                    false, "targetRoute", null);
            observation.steps.add(new WorkflowNodeStep(
                    "ROUTE_GATE", "targetRoute=NEEDS_CONFIRMATION", "BLOCK"));
            return finish(response, observation, startedAt);
        }

        GraphUpdateScope scope = new GraphUpdateScope();
        scope.graphId = context.graph().graphId;
        scope.baseGraphVersion = context.graph().graphVersion;
        scope.route = context.targetRoute();
        scope.targetTopicId = context.targetTopicId();
        scope.proposedTopicTitle = context.proposedTopicTitle();
        context.evidence().forEach(item -> scope.selectedEvidenceIds.add(item.evidenceId));

        if (scope.route == GraphUpdateRoute.UPDATE_EXISTING) {
            GraphNode topic = activeTopic(context, scope.targetTopicId);
            if (topic == null) {
                response.status = GraphUpdateScopeStatus.REJECTED;
                response.policyResult = PolicyResult.BLOCK;
                response.error = new AgentError(AgentErrorCode.INVALID_REQUEST,
                        "targetTopicId must reference an active topic",
                        false, "targetTopicId", null);
                return finish(response, observation, startedAt);
            }
            scope.proposedTopicTitle = topic.title;
            for (GraphNode node : context.graph().nodes) {
                if (node.status != GraphNodeStatus.ACTIVE) continue;
                boolean inBranch = node.id.equals(topic.id)
                        || topic.id.equals(node.topicId);
                if (!inBranch) continue;
                scope.readableNodeIds.add(node.id);
                if (mutable(node.type)) scope.mutableNodeIds.add(node.id);
                else scope.immutableNodeIds.add(node.id);
            }
        } else if (scope.route == GraphUpdateRoute.CREATE_BRANCH
                && blank(scope.proposedTopicTitle)) {
            response.status = GraphUpdateScopeStatus.REJECTED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.MISSING_REQUIRED_FIELD,
                    "CREATE_BRANCH requires proposedTopicTitle",
                    false, "proposedTopicTitle", null);
            return finish(response, observation, startedAt);
        }

        PolicyValidationResult policy = policyValidator.validate(context.graph(), scope);
        response.policyResult = policy.allowed() ? PolicyResult.PASS : PolicyResult.BLOCK;
        if (!policy.allowed()) {
            response.status = GraphUpdateScopeStatus.BLOCKED;
            response.error = new AgentError(policy.errorCode(), policy.message(),
                    false, policy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "SCOPE_POLICY", "plannedScope", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        response.scope = scope;
        response.status = GraphUpdateScopeStatus.READY;
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_PLANNING",
                "route=" + scope.route + ",graphNodeCount=" + context.graph().nodes.size(),
                "readable=" + scope.readableNodeIds.size()
                        + ",mutable=" + scope.mutableNodeIds.size()
                        + ",immutable=" + scope.immutableNodeIds.size()));
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_POLICY", "plannedScope", "PASS"));
        return finish(response, observation, startedAt);
    }

    private GraphNode activeTopic(GraphUpdateScopeContext context, String id) {
        if (blank(id)) return null;
        return context.graph().nodes.stream()
                .filter(node -> id.equals(node.id)
                        && node.type == GraphNodeType.TOPIC
                        && node.status == GraphNodeStatus.ACTIVE)
                .findFirst().orElse(null);
    }

    private boolean mutable(GraphNodeType type) {
        return type == GraphNodeType.TOPIC
                || type == GraphNodeType.PATTERN_HYPOTHESIS
                || type == GraphNodeType.OPEN_QUESTION;
    }

    private GraphUpdateScopeResponse baseResponse(GraphUpdateScopeRequest request) {
        GraphUpdateScopeResponse response = new GraphUpdateScopeResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(GraphUpdateScopeRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.SCOPE_NODE_ID;
        value.nodeVersion = GraphContract.SCOPE_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private GraphUpdateScopeResponse finish(GraphUpdateScopeResponse response,
                                            WorkflowRunObservation observation,
                                            long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.scope != null) {
            observation.evidenceIds = response.scope.selectedEvidenceIds;
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private String inputSummary(GraphUpdateScopeRequest request) {
        int nodes = request == null || request.graph == null || request.graph.nodes == null
                ? 0 : request.graph.nodes.size();
        int evidence = request == null || request.evidence == null ? 0 : request.evidence.size();
        return "graphNodeCount=" + nodes + ",evidenceCount=" + evidence
                + ",route=" + (request == null ? null : request.targetRoute);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
