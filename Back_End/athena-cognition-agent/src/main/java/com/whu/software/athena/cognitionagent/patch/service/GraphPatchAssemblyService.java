package com.whu.software.athena.cognitionagent.patch.service;

import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningDecision;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowNodeStep;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowRunObservation;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
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
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyRequest;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyResponse;
import com.whu.software.athena.cognitionagent.patch.contract.PatchAssemblyStatus;
import com.whu.software.athena.cognitionagent.patch.validation.GraphPatchAssemblyRequestValidator;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import com.whu.software.athena.cognitionagent.scope.policy.GraphUpdateScopePolicyValidator;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GraphPatchAssemblyService {

    private final GraphPatchAssemblyRequestValidator validator;
    private final DeterministicGraphIdFactory ids;
    private final WorkflowTelemetryRecorder telemetry;
    private final GraphUpdateScopePolicyValidator scopePolicyValidator =
            new GraphUpdateScopePolicyValidator();

    @Autowired
    public GraphPatchAssemblyService(WorkflowTelemetryRecorder telemetry) {
        this(new GraphPatchAssemblyRequestValidator(),
                new DeterministicGraphIdFactory(), telemetry);
    }

    GraphPatchAssemblyService(GraphPatchAssemblyRequestValidator validator,
                              DeterministicGraphIdFactory ids,
                              WorkflowTelemetryRecorder telemetry) {
        this.validator = validator;
        this.ids = ids;
        this.telemetry = telemetry;
    }

    public GraphPatchAssemblyResponse assemble(GraphPatchAssemblyRequest request) {
        long startedAt = System.nanoTime();
        GraphPatchAssemblyResponse response = baseResponse(request);
        WorkflowRunObservation observation = observation(request);
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ValidationIssue issue = validation.firstIssue();
            response.status = PatchAssemblyStatus.REJECTED;
            response.schemaResult = SchemaResult.FAIL;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(
                    issue.code(), issue.message(), false, issue.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "INPUT_VALIDATION", inputSummary(request), "REJECTED"));
            return finish(response, observation, startedAt);
        }
        response.schemaResult = SchemaResult.PASS;
        response.policyResult = PolicyResult.PASS;
        observation.steps.add(new WorkflowNodeStep(
                "INPUT_VALIDATION", inputSummary(request), "PASS"));
        PolicyValidationResult scopePolicy =
                scopePolicyValidator.validate(request.graph, request.scope);
        if (!scopePolicy.allowed()) {
            response.status = PatchAssemblyStatus.REJECTED;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(scopePolicy.errorCode(), scopePolicy.message(),
                    false, scopePolicy.field(), null);
            observation.steps.add(new WorkflowNodeStep(
                    "SCOPE_POLICY", "recomputedFromGraph=true", "BLOCK"));
            return finish(response, observation, startedAt);
        }
        observation.steps.add(new WorkflowNodeStep(
                "SCOPE_POLICY", "recomputedFromGraph=true", "PASS"));

        List<SemanticChange> changes = request.semanticDraft.changes.stream()
                .filter(change -> change.changeType != SemanticChangeType.NO_CHANGE)
                .toList();
        if (changes.isEmpty()) {
            response.status = PatchAssemblyStatus.NO_CHANGE;
            observation.steps.add(new WorkflowNodeStep(
                    "CHANGE_GATE", "semanticChangeCount=0", "NO_CHANGE"));
            return finish(response, observation, startedAt);
        }

        try {
            GraphUpdateProposal proposal = baseProposal(request);
            String topicId = request.scope.route == GraphUpdateRoute.CREATE_BRANCH
                    ? ids.id("topic", request.idempotencyKey, "topic")
                    : request.scope.targetTopicId;
            proposal.targetTopicId = topicId;

            List<SourceReference> sources = addSourceEvidence(request, proposal, topicId);
            addOrUpdateTopic(request, proposal, topicId);
            addSemanticChanges(request, proposal, topicId, sources, changes);
            addAction(request, proposal, topicId);
            orderOperations(proposal);

            if (proposal.operations.isEmpty()) {
                response.status = PatchAssemblyStatus.NO_CHANGE;
                return finish(response, observation, startedAt);
            }
            response.proposal = proposal;
            response.status = PatchAssemblyStatus.ASSEMBLED;
            observation.steps.add(new WorkflowNodeStep(
                    "PATCH_ASSEMBLY",
                    "route=" + proposal.route + ",baseGraphVersion=" + proposal.baseGraphVersion,
                    "operationCount=" + proposal.operations.size()
                            + ",targetTopicId=" + proposal.targetTopicId));
        } catch (RuntimeException exception) {
            response.status = PatchAssemblyStatus.FAILED;
            response.schemaResult = SchemaResult.NOT_RUN;
            response.policyResult = PolicyResult.BLOCK;
            response.error = new AgentError(AgentErrorCode.INTERNAL_ERROR,
                    "graph patch assembly failed", false, null, null);
        }
        return finish(response, observation, startedAt);
    }

    private GraphUpdateProposal baseProposal(GraphPatchAssemblyRequest request) {
        GraphUpdateProposal proposal = new GraphUpdateProposal();
        proposal.proposalId = ids.id("proposal", request.idempotencyKey,
                request.graph.graphId + ":" + request.graph.graphVersion);
        proposal.graphId = request.graph.graphId;
        proposal.baseGraphVersion = request.graph.graphVersion;
        proposal.status = GraphProposalStatus.DRAFT;
        proposal.route = request.scope.route;
        proposal.evidenceIds = List.copyOf(request.scope.selectedEvidenceIds);
        proposal.changeSummary = request.semanticDraft.changeSummary;
        proposal.requiresUserConfirmation = true;
        proposal.createdAt = request.proposalCreatedAt;
        return proposal;
    }

    private List<SourceReference> addSourceEvidence(GraphPatchAssemblyRequest request,
                                                    GraphUpdateProposal proposal,
                                                    String topicId) {
        List<SourceReference> sources = new ArrayList<>();
        for (CanonicalEvidence evidence : request.evidence) {
            if (!request.scope.selectedEvidenceIds.contains(evidence.evidenceId)) continue;
            GraphNode existing = findEvidenceNode(request, evidence.evidenceId);
            String sourceNodeId = existing == null
                    ? ids.id("source", request.graph.graphId, evidence.evidenceId)
                    : existing.id;
            sources.add(new SourceReference(evidence.evidenceId, sourceNodeId));
            if (existing == null) {
                GraphNode node = new GraphNode();
                node.id = sourceNodeId;
                node.type = GraphNodeType.SOURCE_EVIDENCE;
                node.status = GraphNodeStatus.ACTIVE;
                node.topicId = topicId;
                node.title = sourceTitle(evidence.sourceType);
                node.content = evidence.summary;
                node.evidenceIds = List.of(evidence.evidenceId);
                node.createdAt = request.proposalCreatedAt;
                node.updatedAt = request.proposalCreatedAt;
                proposal.operations.add(addNode(node, List.of(evidence.evidenceId),
                        "补充本次整理所依据的原始内容。"));
            }
            addEdgeIfAbsent(request, proposal, GraphEdgeType.ABOUT,
                    sourceNodeId, topicId, List.of(evidence.evidenceId),
                    "把原始内容关联到目标主题。");
        }
        return sources;
    }

    private void addOrUpdateTopic(GraphPatchAssemblyRequest request,
                                  GraphUpdateProposal proposal,
                                  String topicId) {
        if (request.scope.route == GraphUpdateRoute.CREATE_BRANCH) {
            GraphNode topic = new GraphNode();
            topic.id = topicId;
            topic.type = GraphNodeType.TOPIC;
            topic.status = GraphNodeStatus.ACTIVE;
            topic.title = request.semanticDraft.topicTitle;
            topic.content = request.semanticDraft.stageUnderstanding;
            topic.evidenceIds = List.copyOf(
                    request.semanticDraft.stageUnderstandingEvidenceIds);
            topic.createdAt = request.proposalCreatedAt;
            topic.updatedAt = request.proposalCreatedAt;
            proposal.operations.add(addNode(topic, topic.evidenceIds,
                    "建立一个等待用户确认的观察主题。"));
            return;
        }
        GraphNode existing = request.graph.nodes.stream()
                .filter(node -> topicId.equals(node.id)).findFirst().orElseThrow();
        GraphNode updated = copyNode(existing);
        updated.content = request.semanticDraft.stageUnderstanding;
        updated.evidenceIds = merged(existing.evidenceIds,
                request.semanticDraft.stageUnderstandingEvidenceIds);
        updated.updatedAt = request.proposalCreatedAt;
        updated.version = existing.version + 1;
        proposal.operations.add(updateNode(updated,
                request.semanticDraft.stageUnderstandingEvidenceIds,
                "仅刷新目标主题的阶段理解。"));
    }

    private void addSemanticChanges(GraphPatchAssemblyRequest request,
                                    GraphUpdateProposal proposal,
                                    String topicId,
                                    List<SourceReference> sources,
                                    List<SemanticChange> changes) {
        for (int index = 0; index < changes.size(); index++) {
            SemanticChange change = changes.get(index);
            String semanticNodeId;
            if (change.changeType == SemanticChangeType.ADD) {
                semanticNodeId = ids.id("semantic", request.idempotencyKey,
                        change.nodeType.name() + ":" + index);
                GraphNode node = new GraphNode();
                node.id = semanticNodeId;
                node.type = change.nodeType;
                node.status = GraphNodeStatus.ACTIVE;
                node.topicId = topicId;
                node.content = change.content;
                node.evidenceIds = List.copyOf(change.evidenceIds);
                node.createdAt = request.proposalCreatedAt;
                node.updatedAt = request.proposalCreatedAt;
                proposal.operations.add(addNode(node, change.evidenceIds,
                        "根据这条依据补充一项认知内容。"));
            } else {
                GraphNode existing = request.graph.nodes.stream()
                        .filter(node -> node.id.equals(change.targetNodeId))
                        .findFirst().orElseThrow();
                semanticNodeId = existing.id;
                GraphNode updated = copyNode(existing);
                updated.content = change.content;
                updated.evidenceIds = merged(existing.evidenceIds, change.evidenceIds);
                updated.updatedAt = request.proposalCreatedAt;
                updated.version = existing.version + 1;
                proposal.operations.add(updateNode(updated, change.evidenceIds,
                        "修订一条可修改的认知内容，并保留其历史。"));
            }
            for (String evidenceId : change.evidenceIds) {
                sources.stream().filter(source -> source.evidenceId.equals(evidenceId))
                        .findFirst().ifPresent(source -> addEdgeIfAbsent(
                                request, proposal, GraphEdgeType.GROUNDS,
                                source.nodeId, semanticNodeId, List.of(evidenceId),
                                "把这项认知内容锚定到对应的原始依据。"));
            }
        }
    }

    private void addAction(GraphPatchAssemblyRequest request,
                           GraphUpdateProposal proposal,
                           String topicId) {
        if (request.actionPlan.decision == ActionPlanningDecision.KEEP_EXISTING) return;
        String actionId = ids.id("action", request.idempotencyKey, "next-action");
        GraphNode action = new GraphNode();
        action.id = actionId;
        action.type = GraphNodeType.ACTION;
        action.status = GraphNodeStatus.ACTIVE;
        action.topicId = topicId;
        action.title = request.actionPlan.title;
        action.content = request.actionPlan.description;
        action.evidenceIds = List.copyOf(request.actionPlan.evidenceIds);
        action.actionType = request.actionPlan.actionType;
        action.actionStatus = GraphActionStatus.PENDING;
        action.dueAt = request.actionPlan.dueAt;
        action.feedbackOptions = List.copyOf(request.actionPlan.feedbackOptions);
        action.createdAt = request.proposalCreatedAt;
        action.updatedAt = request.proposalCreatedAt;
        proposal.operations.add(addNode(action, request.actionPlan.evidenceIds,
                request.actionPlan.rationale));
        addEdgeIfAbsent(request, proposal, GraphEdgeType.NEXT_STEP_FOR,
                actionId, topicId, request.actionPlan.evidenceIds,
                "把建议的观察行动关联到目标主题。");
    }

    private void addEdgeIfAbsent(GraphPatchAssemblyRequest request,
                                 GraphUpdateProposal proposal,
                                 GraphEdgeType type,
                                 String from,
                                 String to,
                                 List<String> evidenceIds,
                                 String reason) {
        boolean graphHasEdge = request.graph.edges.stream().anyMatch(edge -> edge.active
                && edge.type == type && edge.fromNodeId.equals(from) && edge.toNodeId.equals(to));
        boolean proposalHasEdge = proposal.operations.stream()
                .filter(operation -> operation.operationType == GraphOperationType.ADD_EDGE)
                .map(operation -> operation.edge)
                .anyMatch(edge -> edge.type == type
                        && edge.fromNodeId.equals(from) && edge.toNodeId.equals(to));
        if (graphHasEdge || proposalHasEdge) return;
        GraphEdge edge = new GraphEdge();
        edge.id = ids.id("edge", request.graph.graphId,
                type.name() + ":" + from + ":" + to);
        edge.type = type;
        edge.fromNodeId = from;
        edge.toNodeId = to;
        edge.evidenceIds = List.copyOf(evidenceIds);
        edge.createdAt = request.proposalCreatedAt;
        edge.updatedAt = request.proposalCreatedAt;
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.ADD_EDGE;
        operation.edge = edge;
        operation.evidenceIds = List.copyOf(evidenceIds);
        operation.reason = reason;
        proposal.operations.add(operation);
    }

    private GraphPatchOperation addNode(GraphNode node,
                                        List<String> evidenceIds,
                                        String reason) {
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.ADD_NODE;
        operation.node = node;
        operation.evidenceIds = List.copyOf(evidenceIds);
        operation.reason = reason;
        return operation;
    }

    private GraphPatchOperation updateNode(GraphNode node,
                                           List<String> evidenceIds,
                                           String reason) {
        GraphPatchOperation operation = new GraphPatchOperation();
        operation.operationType = GraphOperationType.UPDATE_NODE;
        operation.targetId = node.id;
        operation.node = node;
        operation.evidenceIds = List.copyOf(evidenceIds);
        operation.reason = reason;
        return operation;
    }

    /**
     * User-facing Chinese display title for a source-evidence node; the enum value
     * itself stays the contract identifier and is never shown to users.
     */
    private String sourceTitle(com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType sourceType) {
        return switch (sourceType) {
            case ARTICLE_HIGHLIGHT -> "文章标记";
            case BODY_RECORD -> "身体记录";
            case ACTION_FEEDBACK -> "行动反馈";
        };
    }

    private GraphNode findEvidenceNode(GraphPatchAssemblyRequest request, String evidenceId) {
        return request.graph.nodes.stream()
                .filter(node -> node.type == GraphNodeType.SOURCE_EVIDENCE
                        && node.evidenceIds != null && node.evidenceIds.contains(evidenceId))
                .findFirst().orElse(null);
    }

    private GraphNode copyNode(GraphNode source) {
        GraphNode target = new GraphNode();
        target.id = source.id;
        target.type = source.type;
        target.status = source.status;
        target.topicId = source.topicId;
        target.title = source.title;
        target.content = source.content;
        target.domain = source.domain;
        target.evidenceIds = source.evidenceIds == null
                ? new ArrayList<>() : new ArrayList<>(source.evidenceIds);
        target.actionType = source.actionType;
        target.actionStatus = source.actionStatus;
        target.dueAt = source.dueAt;
        target.feedbackOptions = source.feedbackOptions == null
                ? new ArrayList<>() : new ArrayList<>(source.feedbackOptions);
        target.createdAt = source.createdAt;
        target.updatedAt = source.updatedAt;
        target.version = source.version;
        return target;
    }

    private List<String> merged(List<String> left, List<String> right) {
        Set<String> values = new LinkedHashSet<>();
        if (left != null) values.addAll(left);
        if (right != null) values.addAll(right);
        return List.copyOf(values);
    }

    private void orderOperations(GraphUpdateProposal proposal) {
        proposal.operations.sort((left, right) -> Integer.compare(
                operationPriority(left.operationType), operationPriority(right.operationType)));
    }

    private int operationPriority(GraphOperationType type) {
        return switch (type) {
            case ADD_NODE, UPDATE_NODE, SUPERSEDE_NODE -> 0;
            case ADD_EDGE -> 1;
            case DEACTIVATE_EDGE -> 2;
            case NO_OP -> 3;
        };
    }

    private GraphPatchAssemblyResponse baseResponse(GraphPatchAssemblyRequest request) {
        GraphPatchAssemblyResponse response = new GraphPatchAssemblyResponse();
        response.runId = request == null ? null : request.runId;
        return response;
    }

    private WorkflowRunObservation observation(GraphPatchAssemblyRequest request) {
        WorkflowRunObservation value = new WorkflowRunObservation();
        value.runId = request == null ? null : request.runId;
        value.triggerType = request == null || request.triggerType == null
                ? null : request.triggerType.name();
        value.workflowVersion = GraphContract.WORKFLOW_VERSION;
        value.nodeId = GraphContract.PATCH_ASSEMBLY_NODE_ID;
        value.nodeVersion = GraphContract.PATCH_ASSEMBLY_NODE_VERSION;
        value.modelProvider = "none";
        value.contextSnapshotId = request == null ? null : request.contextSnapshotId;
        return value;
    }

    private GraphPatchAssemblyResponse finish(GraphPatchAssemblyResponse response,
                                              WorkflowRunObservation observation,
                                              long startedAt) {
        observation.latencyMs = Math.max(0L,
                (System.nanoTime() - startedAt) / 1_000_000L);
        observation.schemaResult = response.schemaResult;
        observation.policyResult = response.policyResult;
        if (response.proposal != null) observation.evidenceIds = response.proposal.evidenceIds;
        observation.steps.add(new WorkflowNodeStep(
                "FINAL_RESPONSE", "runId=" + response.runId,
                "status=" + response.status));
        response.observation = observation;
        telemetry.record(response.status == null ? "FAILED" : response.status.name(), observation);
        return response;
    }

    private String inputSummary(GraphPatchAssemblyRequest request) {
        int changes = request == null || request.semanticDraft == null
                || request.semanticDraft.changes == null
                ? 0 : request.semanticDraft.changes.size();
        return "route=" + (request == null || request.scope == null
                ? null : request.scope.route) + ",semanticChangeCount=" + changes;
    }

    private record SourceReference(String evidenceId, String nodeId) {
    }
}
