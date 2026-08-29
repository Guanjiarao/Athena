package com.whu.software.athena.cognitionagent.feedbackgraph.service;

import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.feedback.contract.NormalizedActionFeedback;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateRequest;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateResponse;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateStatus;
import com.whu.software.athena.cognitionagent.feedbackgraph.validation.FeedbackGraphUpdateRequestValidator;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdgeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphOperationType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphPatchOperation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphProposalStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.support.GraphContractCopier;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.PatchGuardStatus;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.patch.service.DeterministicGraphIdFactory;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class FeedbackGraphUpdateService {

    private final FeedbackGraphUpdateRequestValidator validator =
            new FeedbackGraphUpdateRequestValidator();
    private final DeterministicGraphIdFactory ids = new DeterministicGraphIdFactory();
    private final GraphContractCopier copier = new GraphContractCopier();
    private final GraphPatchGuardService guardService;
    private final WorkflowTelemetryRecorder telemetry;

    public FeedbackGraphUpdateService(GraphPatchGuardService guardService,
                                      WorkflowTelemetryRecorder telemetry) {
        this.guardService = guardService;
        this.telemetry = telemetry;
    }

    public FeedbackGraphUpdateResponse prepare(FeedbackGraphUpdateRequest request) {
        long startedAt = System.nanoTime();
        FeedbackGraphUpdateResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = FeedbackGraphUpdateStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(issue.code(), issue.message(), false,
                    issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));

        if (request.normalizedFeedback.baseGraphVersion != request.graph.graphVersion) {
            response.status = FeedbackGraphUpdateStatus.STALE;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.GRAPH_VERSION_CONFLICT,
                    "feedback was normalized against a stale graph version", false,
                    "normalizedFeedback.baseGraphVersion", null);
            observation.steps.add(new WorkflowNodeStep(
                    "VERSION_GUARD", "graphVersion=" + request.graph.graphVersion,
                    "feedbackVersion=" + request.normalizedFeedback.baseGraphVersion
                            + ":STALE"));
            return finish(response, observation, startedAt);
        }

        GraphNode action = request.graph.nodes.stream()
                .filter(node -> request.normalizedFeedback.actionId.equals(node.id))
                .findFirst().orElse(null);
        if (!consistent(request.normalizedFeedback, action)) {
            response.status = FeedbackGraphUpdateStatus.BLOCKED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.POLICY_BLOCKED,
                    "normalized feedback does not match the pending graph action",
                    false, "normalizedFeedback", null);
            return finish(response, observation, startedAt);
        }

        GraphUpdateScope scope = scope(request, action);
        GraphUpdateProposal draft = proposal(request, action);
        GraphPatchGuardRequest guardRequest = guardRequest(request, scope, draft);
        response.guardResult = guardService.guard(guardRequest);
        if (response.guardResult.status != PatchGuardStatus.READY_FOR_CONFIRMATION) {
            response.status = switch (response.guardResult.status) {
                case STALE -> FeedbackGraphUpdateStatus.STALE;
                case BLOCKED -> FeedbackGraphUpdateStatus.BLOCKED;
                case REJECTED -> FeedbackGraphUpdateStatus.REJECTED;
                default -> FeedbackGraphUpdateStatus.FAILED;
            };
            response.policyResult = response.guardResult.policyResult;
            response.error = response.guardResult.error;
            return finish(response, observation, startedAt);
        }

        response.proposal = response.guardResult.proposal;
        response.graphPreview = response.guardResult.graphPreview;
        response.status = FeedbackGraphUpdateStatus.READY_FOR_CONFIRMATION;
        response.policyResult = PolicyResult.PASS;
        observation.evidenceIds = List.of(
                request.normalizedFeedback.evidence.evidenceId);
        observation.steps.add(new WorkflowNodeStep(
                "FEEDBACK_PATCH",
                "result=" + request.normalizedFeedback.result,
                "operationCount=" + response.proposal.operations.size()));
        observation.steps.add(new WorkflowNodeStep(
                "GRAPH_PREVIEW",
                "baseGraphVersion=" + request.graph.graphVersion,
                "previewGraphVersion=" + response.graphPreview.graphVersion));
        return finish(response, observation, startedAt);
    }

    private boolean consistent(NormalizedActionFeedback feedback, GraphNode action) {
        if (action == null || action.type != GraphNodeType.ACTION
                || action.status != GraphNodeStatus.ACTIVE
                || action.actionStatus
                != GraphActionStatus.PENDING
                || !feedback.topicId.equals(action.topicId)
                || !feedback.actionId.equals(feedback.evidence.relatedActionId)
                || feedback.result != feedback.evidence.feedbackResult) {
            return false;
        }
        var expected = feedback.result == GraphActionFeedbackResult.SKIPPED
                ? GraphActionStatus.SKIPPED : GraphActionStatus.COMPLETED;
        return feedback.resultingActionStatus == expected
                && action.feedbackOptions.contains(feedback.result);
    }

    private GraphUpdateScope scope(FeedbackGraphUpdateRequest request, GraphNode action) {
        GraphUpdateScope value = new GraphUpdateScope();
        value.graphId = request.graph.graphId;
        value.baseGraphVersion = request.graph.graphVersion;
        value.route = GraphUpdateRoute.UPDATE_EXISTING;
        value.targetTopicId = action.topicId;
        value.selectedEvidenceIds = List.of(
                request.normalizedFeedback.evidence.evidenceId);
        for (GraphNode node : request.graph.nodes) {
            if (node.status != GraphNodeStatus.ACTIVE) continue;
            boolean inBranch = node.id.equals(action.topicId)
                    || action.topicId.equals(node.topicId);
            if (!inBranch) continue;
            value.readableNodeIds.add(node.id);
            if (node.id.equals(action.id)
                    || node.type == GraphNodeType.TOPIC
                    || node.type == GraphNodeType.PATTERN_HYPOTHESIS
                    || node.type == GraphNodeType.OPEN_QUESTION) {
                value.mutableNodeIds.add(node.id);
            } else {
                value.immutableNodeIds.add(node.id);
            }
        }
        return value;
    }

    private GraphUpdateProposal proposal(FeedbackGraphUpdateRequest request,
                                         GraphNode action) {
        NormalizedActionFeedback feedback = request.normalizedFeedback;
        String evidenceId = feedback.evidence.evidenceId;
        GraphUpdateProposal proposal = new GraphUpdateProposal();
        proposal.proposalId = ids.id("proposal", request.idempotencyKey,
                feedback.feedbackId + ":" + request.graph.graphVersion);
        proposal.graphId = request.graph.graphId;
        proposal.baseGraphVersion = request.graph.graphVersion;
        proposal.status = GraphProposalStatus.DRAFT;
        proposal.route = GraphUpdateRoute.UPDATE_EXISTING;
        proposal.targetTopicId = feedback.topicId;
        proposal.evidenceIds = List.of(evidenceId);
        proposal.changeSummary = "记录行动反馈并关闭本次观察。";
        proposal.requiresUserConfirmation = true;
        proposal.workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
        proposal.createdAt = feedback.evidence.occurredAt;

        GraphNode updatedAction = copier.node(action);
        updatedAction.actionStatus = feedback.resultingActionStatus;
        updatedAction.evidenceIds = merged(action.evidenceIds, List.of(evidenceId));
        updatedAction.updatedAt = feedback.evidence.occurredAt;
        updatedAction.version = action.version + 1;
        proposal.operations.add(updateNode(updatedAction, evidenceId,
                "根据用户反馈关闭本次观察行动。"));

        String sourceId = ids.id("source", request.idempotencyKey,
                feedback.feedbackId + ":source");
        GraphNode source = new GraphNode();
        source.id = sourceId;
        source.type = GraphNodeType.SOURCE_EVIDENCE;
        source.status = GraphNodeStatus.ACTIVE;
        source.topicId = feedback.topicId;
        source.title = "行动反馈";
        source.content = feedback.evidence.summary;
        source.evidenceIds = List.of(evidenceId);
        source.createdAt = feedback.evidence.occurredAt;
        source.updatedAt = feedback.evidence.occurredAt;
        proposal.operations.add(addNode(source, evidenceId,
                "把这次反馈作为可追溯的原始记录。"));

        addEdge(proposal, request, GraphEdgeType.ABOUT, sourceId,
                feedback.topicId, evidenceId, "把反馈记录关联到所属主题。");
        addEdge(proposal, request, GraphEdgeType.FEEDBACK_FOR, sourceId,
                action.id, evidenceId, "把反馈记录关联到对应行动。");

        if (feedback.result != GraphActionFeedbackResult.SKIPPED) {
            GraphNode semantic = semanticNode(request, feedback);
            proposal.operations.add(addNode(semantic, evidenceId,
                    "根据反馈补充一项有边界的认知内容。"));
            addEdge(proposal, request, GraphEdgeType.GROUNDS, sourceId,
                    semantic.id, evidenceId, "把认知内容关联到它的反馈来源。");
        }
        proposal.operations.sort((left, right) -> Integer.compare(
                operationPriority(left.operationType), operationPriority(right.operationType)));
        return proposal;
    }

    private GraphNode semanticNode(FeedbackGraphUpdateRequest request,
                                   NormalizedActionFeedback feedback) {
        GraphNode node = new GraphNode();
        node.id = ids.id("semantic", request.idempotencyKey,
                feedback.feedbackId + ":meaning");
        node.type = feedback.result == GraphActionFeedbackResult.UNCERTAIN
                ? GraphNodeType.OPEN_QUESTION : GraphNodeType.SELF_REPORTED_FACT;
        node.status = GraphNodeStatus.ACTIVE;
        node.topicId = feedback.topicId;
        node.content = switch (feedback.result) {
            case OCCURRED -> "用户反馈这次观察的情况出现了。";
            case NOT_OCCURRED ->
                    "用户反馈这次观察的情况本次未出现。";
            case UNCERTAIN -> "用户反馈这次观察的结果仍不确定。";
            case SKIPPED -> throw new IllegalStateException("skipped feedback has no meaning node");
        };
        node.evidenceIds = List.of(feedback.evidence.evidenceId);
        node.createdAt = feedback.evidence.occurredAt;
        node.updatedAt = feedback.evidence.occurredAt;
        return node;
    }

    private GraphPatchGuardRequest guardRequest(FeedbackGraphUpdateRequest request,
                                                GraphUpdateScope scope,
                                                GraphUpdateProposal proposal) {
        GraphPatchGuardRequest value = new GraphPatchGuardRequest();
        value.runId = request.runId;
        value.idempotencyKey = request.idempotencyKey + ":guard";
        value.triggerType = request.triggerType;
        value.contextSnapshotId = request.contextSnapshotId;
        value.graph = request.graph;
        value.evidence = List.of(request.normalizedFeedback.evidence);
        value.scope = scope;
        value.proposal = proposal;
        return value;
    }

    private GraphPatchOperation addNode(GraphNode node,
                                        String evidenceId,
                                        String reason) {
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.ADD_NODE;
        operation.node = node;
        operation.evidenceIds = List.of(evidenceId);
        operation.reason = reason;
        return operation;
    }

    private GraphPatchOperation updateNode(GraphNode node,
                                           String evidenceId,
                                           String reason) {
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.UPDATE_NODE;
        operation.targetId = node.id;
        operation.node = node;
        operation.evidenceIds = List.of(evidenceId);
        operation.reason = reason;
        return operation;
    }

    private void addEdge(GraphUpdateProposal proposal,
                         FeedbackGraphUpdateRequest request,
                         GraphEdgeType type,
                         String from,
                         String to,
                         String evidenceId,
                         String reason) {
        GraphEdge edge = new GraphEdge();
        edge.id = ids.id("edge", request.idempotencyKey,
                type + ":" + from + ":" + to);
        edge.type = type;
        edge.fromNodeId = from;
        edge.toNodeId = to;
        edge.evidenceIds = List.of(evidenceId);
        edge.createdAt = request.normalizedFeedback.evidence.occurredAt;
        edge.updatedAt = edge.createdAt;
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.ADD_EDGE;
        operation.edge = edge;
        operation.evidenceIds = List.of(evidenceId);
        operation.reason = reason;
        proposal.operations.add(operation);
    }

    private List<String> merged(List<String> left, List<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (left != null) values.addAll(left);
        values.addAll(right);
        return new ArrayList<>(values);
    }

    private int operationPriority(GraphOperationType type) {
        return switch (type) {
            case ADD_NODE, UPDATE_NODE, SUPERSEDE_NODE -> 0;
            case ADD_EDGE -> 1;
            case DEACTIVATE_EDGE -> 2;
            case NO_OP -> 3;
        };
    }

    private FeedbackGraphUpdateResponse baseResponse(FeedbackGraphUpdateRequest request) {
        FeedbackGraphUpdateResponse response = new FeedbackGraphUpdateResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(FeedbackGraphUpdateRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.FEEDBACK_WORKFLOW_VERSION;
        value.nodeId = GraphContract.FEEDBACK_GRAPH_NODE_ID;
        value.nodeVersion = GraphContract.FEEDBACK_GRAPH_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        value.feedbackResult = request == null || request.normalizedFeedback == null
                || request.normalizedFeedback.result == null
                ? null : request.normalizedFeedback.result.name();
        value.baseGraphVersion = request == null || request.graph == null
                ? null : request.graph.graphVersion;
        return value;
    }

    private FeedbackGraphUpdateResponse finish(
            FeedbackGraphUpdateResponse response,
            WorkflowRunObservation observation,
            long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.proposal != null) observation.evidenceIds = response.proposal.evidenceIds;
        if (response.proposal != null) {
            observation.operationCount = response.proposal.operations.size();
        }
        if (response.graphPreview != null) {
            observation.previewGraphVersion = response.graphPreview.graphVersion;
        }
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(),
                observation);
        return response;
    }

    private String inputSummary(FeedbackGraphUpdateRequest request) {
        return "graphVersion=" + (request == null || request.graph == null
                ? null : request.graph.graphVersion)
                + ",normalizedFeedbackPresent="
                + (request != null && request.normalizedFeedback != null);
    }
}
