package com.whu.software.athena.cognitionagent.guard.policy;

import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
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
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.graph.policy.GraphTextPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;

public class GraphPatchPolicyValidator {

    private static final Set<GraphActionType> ALLOWED_ACTION_TYPES = Set.of(
            GraphActionType.RECORD_BODY,
            GraphActionType.RECORD_MOOD,
            GraphActionType.RECORD_SLEEP,
            GraphActionType.ANSWER_QUESTION,
            GraphActionType.CONFIRM_STATUS);
    private static final Set<GraphActionFeedbackResult> REQUIRED_FEEDBACK = Set.of(
            GraphActionFeedbackResult.OCCURRED,
            GraphActionFeedbackResult.NOT_OCCURRED,
            GraphActionFeedbackResult.UNCERTAIN,
            GraphActionFeedbackResult.SKIPPED);
    private final GraphTextPolicyValidator textPolicy = new GraphTextPolicyValidator();

    public PolicyValidationResult validate(PersonalCognitionGraph graph,
                                           List<CanonicalEvidence> evidence,
                                           GraphUpdateScope scope,
                                           GraphUpdateProposal proposal) {
        if (!graph.graphId.equals(proposal.graphId)) {
            return block("proposal.graphId", "proposal must target the supplied graph");
        }
        if (proposal.status != GraphProposalStatus.DRAFT) {
            return block("proposal.status", "guard only accepts a DRAFT proposal");
        }
        if (!proposal.requiresUserConfirmation) {
            return block("proposal.requiresUserConfirmation",
                    "every graph proposal requires human confirmation");
        }
        if (proposal.route != scope.route) {
            return block("proposal.route", "proposal route must match the frozen scope");
        }
        if (!sameSet(proposal.evidenceIds, scope.selectedEvidenceIds)) {
            return block("proposal.evidenceIds",
                    "proposal evidence must equal the frozen selected evidence set");
        }
        Map<String, CanonicalEvidence> evidenceById = new HashMap<>();
        evidence.forEach(item -> evidenceById.put(item.evidenceId, item));
        if (!evidenceById.keySet().containsAll(proposal.evidenceIds)) {
            return block("proposal.evidenceIds", "proposal cites unavailable evidence");
        }
        Map<String, GraphNode> existingNodes = new HashMap<>();
        graph.nodes.forEach(node -> existingNodes.put(node.id, node));
        Map<String, GraphEdge> existingEdges = new HashMap<>();
        graph.edges.forEach(edge -> existingEdges.put(edge.id, edge));
        Map<String, GraphNode> addedNodes = new HashMap<>();
        Map<String, GraphEdge> addedEdges = new HashMap<>();
        Set<String> changedTargets = new HashSet<>();
        Set<String> activeEdgeRelations = new HashSet<>();
        existingEdges.values().stream().filter(edge -> edge.active)
                .forEach(edge -> activeEdgeRelations.add(edgeKey(edge)));
        int addedTopicCount = 0;
        int addedActionCount = 0;
        int feedbackActionUpdateCount = 0;
        int feedbackSourceCount = 0;
        int feedbackForEdgeCount = 0;
        List<GraphNodeType> feedbackSemanticTypes = new ArrayList<>();
        int noOpCount = 0;

        for (int index = 0; index < proposal.operations.size(); index++) {
            GraphPatchOperation operation = proposal.operations.get(index);
            String field = "proposal.operations[" + index + "]";
            if (!new HashSet<>(proposal.evidenceIds).containsAll(operation.evidenceIds)) {
                return block(field + ".evidenceIds",
                        "operation cites evidence outside the proposal");
            }
            PolicyValidationResult reason = textPolicy.validate(field + ".reason", operation.reason);
            if (!reason.allowed()) return reason;
            switch (operation.operationType) {
                case ADD_NODE -> {
                    if (operation.node == null || operation.edge != null
                            || operation.targetId != null || operation.supersededByNodeId != null) {
                        return block(field, "ADD_NODE has an invalid operation shape");
                    }
                    GraphNode node = operation.node;
                    if (blank(node.id) || existingNodes.containsKey(node.id)
                            || addedNodes.putIfAbsent(node.id, node) != null) {
                        return block(field + ".node.id", "added node id must be new and unique");
                    }
                    PolicyValidationResult nodePolicy = validateAddedNode(
                            field + ".node", node, operation.evidenceIds,
                            evidenceById, proposal.targetTopicId);
                    if (!nodePolicy.allowed()) return nodePolicy;
                    if (node.type == GraphNodeType.TOPIC) addedTopicCount++;
                    if (node.type == GraphNodeType.ACTION) addedActionCount++;
                    if (node.type == GraphNodeType.SOURCE_EVIDENCE
                            && operation.evidenceIds.stream().anyMatch(id ->
                            actionFeedback(evidenceById.get(id)))) {
                        feedbackSourceCount++;
                    }
                    if (semantic(node.type)
                            && operation.evidenceIds.stream().anyMatch(id ->
                            actionFeedback(evidenceById.get(id)))) {
                        feedbackSemanticTypes.add(node.type);
                    }
                }
                case UPDATE_NODE -> {
                    if (operation.node == null || operation.edge != null
                            || blank(operation.targetId)
                            || !operation.targetId.equals(operation.node.id)
                            || operation.supersededByNodeId != null) {
                        return block(field, "UPDATE_NODE has an invalid operation shape");
                    }
                    if (!changedTargets.add(operation.targetId)) {
                        return block(field + ".targetId", "a node may be changed only once");
                    }
                    GraphNode current = existingNodes.get(operation.targetId);
                    PolicyValidationResult update = validateUpdatedNode(
                            field + ".node", current, operation.node,
                            operation.evidenceIds, evidenceById, scope);
                    if (!update.allowed()) return update;
                    if (current != null && current.type == GraphNodeType.ACTION) {
                        feedbackActionUpdateCount++;
                    }
                }
                case ADD_EDGE -> {
                    if (operation.edge == null || operation.node != null
                            || operation.targetId != null || operation.supersededByNodeId != null) {
                        return block(field, "ADD_EDGE has an invalid operation shape");
                    }
                    GraphEdge edge = operation.edge;
                    if (blank(edge.id) || existingEdges.containsKey(edge.id)
                            || addedEdges.putIfAbsent(edge.id, edge) != null) {
                        return block(field + ".edge.id", "added edge id must be new and unique");
                    }
                    if (!sameSet(edge.evidenceIds, operation.evidenceIds)) {
                        return block(field + ".edge.evidenceIds",
                                "new edge evidence must equal the operation evidence set");
                    }
                    if (!activeEdgeRelations.add(edgeKey(edge))) {
                        return block(field + ".edge",
                                "an active relation with the same type and endpoints already exists");
                    }
                    if (edge.type == GraphEdgeType.FEEDBACK_FOR) feedbackForEdgeCount++;
                }
                case SUPERSEDE_NODE -> {
                    if (blank(operation.targetId) || blank(operation.supersededByNodeId)
                            || operation.node != null || operation.edge != null
                            || !changedTargets.add(operation.targetId)) {
                        return block(field, "SUPERSEDE_NODE has an invalid operation shape");
                    }
                    GraphNode current = existingNodes.get(operation.targetId);
                    if (current == null || !scope.mutableNodeIds.contains(current.id)) {
                        return block(field + ".targetId",
                                "only a mutable node in the target scope may be superseded");
                    }
                }
                case DEACTIVATE_EDGE -> {
                    if (blank(operation.targetId) || operation.node != null
                            || operation.edge != null || operation.supersededByNodeId != null
                            || !existingEdges.containsKey(operation.targetId)) {
                        return block(field, "DEACTIVATE_EDGE has an invalid operation shape");
                    }
                }
                case NO_OP -> {
                    noOpCount++;
                    if (proposal.operations.size() != 1
                            || proposal.route != GraphUpdateRoute.NO_CHANGE
                            || operation.node != null || operation.edge != null
                            || operation.targetId != null || operation.supersededByNodeId != null) {
                        return block(field, "NO_OP must be the only operation on a NO_CHANGE route");
                    }
                }
            }
        }

        Set<String> allNodeIds = new HashSet<>(existingNodes.keySet());
        allNodeIds.addAll(addedNodes.keySet());
        for (Map.Entry<String, GraphEdge> entry : addedEdges.entrySet()) {
            PolicyValidationResult edgePolicy = validateAddedEdge(
                    "edge[" + entry.getKey() + "]", entry.getValue(), allNodeIds,
                    existingNodes, addedNodes, evidenceById, proposal.targetTopicId);
            if (!edgePolicy.allowed()) return edgePolicy;
        }
        for (GraphPatchOperation operation : proposal.operations) {
            if (operation.operationType == GraphOperationType.SUPERSEDE_NODE) {
                GraphNode oldNode = existingNodes.get(operation.targetId);
                GraphNode replacement = addedNodes.get(operation.supersededByNodeId);
                if (replacement == null || oldNode.type != replacement.type) {
                    return block("proposal.operations.supersededByNodeId",
                            "replacement node must be added by this proposal with the same type");
                }
            }
        }
        if (proposal.route == GraphUpdateRoute.CREATE_BRANCH) {
            if (addedTopicCount != 1 || !addedNodes.containsKey(proposal.targetTopicId)) {
                return block("proposal.targetTopicId",
                        "CREATE_BRANCH must add exactly its target topic");
            }
        } else if (proposal.route == GraphUpdateRoute.UPDATE_EXISTING) {
            GraphNode topic = existingNodes.get(proposal.targetTopicId);
            if (topic == null || topic.type != GraphNodeType.TOPIC
                    || topic.status != GraphNodeStatus.ACTIVE || addedTopicCount != 0) {
                return block("proposal.targetTopicId",
                        "UPDATE_EXISTING must target one active existing topic");
            }
        }
        if (addedActionCount > 1) {
            return block("proposal.operations", "a proposal may add at most one next action");
        }
        if (noOpCount > 0 && proposal.route != GraphUpdateRoute.NO_CHANGE) {
            return block("proposal.operations", "NO_OP is not allowed on a changing route");
        }
        List<CanonicalEvidence> feedbackEvidence = proposal.evidenceIds.stream()
                .map(evidenceById::get).filter(this::actionFeedback).toList();
        if (!feedbackEvidence.isEmpty()) {
            if (feedbackEvidence.size() != 1 || proposal.evidenceIds.size() != 1
                    || feedbackActionUpdateCount != 1 || feedbackSourceCount != 1
                    || feedbackForEdgeCount != 1) {
                return block("proposal.operations",
                        "feedback patch must atomically close one action and add one source and FEEDBACK_FOR edge");
            }
            GraphActionFeedbackResult result = feedbackEvidence.get(0).feedbackResult;
            if (result == GraphActionFeedbackResult.SKIPPED
                    && !feedbackSemanticTypes.isEmpty()) {
                return block("proposal.operations",
                        "skipped feedback cannot create semantic body meaning");
            }
            if (result == GraphActionFeedbackResult.UNCERTAIN
                    && !feedbackSemanticTypes.equals(List.of(GraphNodeType.OPEN_QUESTION))) {
                return block("proposal.operations",
                        "uncertain feedback must create exactly one open question");
            }
            if ((result == GraphActionFeedbackResult.OCCURRED
                    || result == GraphActionFeedbackResult.NOT_OCCURRED)
                    && !feedbackSemanticTypes.equals(List.of(GraphNodeType.SELF_REPORTED_FACT))) {
                return block("proposal.operations",
                        "observed feedback must create exactly one self-reported fact");
            }
        }
        return PolicyValidationResult.pass();
    }

    private PolicyValidationResult validateAddedNode(String field,
                                                     GraphNode node,
                                                     List<String> operationEvidence,
                                                     Map<String, CanonicalEvidence> evidence,
                                                     String topicId) {
        if (node.type == null || node.status != GraphNodeStatus.ACTIVE || node.version != 1) {
            return block(field, "new nodes require type, ACTIVE status, and version 1");
        }
        if (!sameSet(node.evidenceIds, operationEvidence)) {
            return block(field + ".evidenceIds",
                    "new node evidence must equal the operation evidence set");
        }
        if (node.type == GraphNodeType.TOPIC) {
            if (node.topicId != null || !node.id.equals(topicId) || blank(node.title)) {
                return block(field, "the new topic shape is invalid");
            }
        } else if (!topicId.equals(node.topicId)) {
            return block(field + ".topicId", "new child nodes must belong to the target topic");
        }
        if (node.type != GraphNodeType.SOURCE_EVIDENCE) {
            PolicyValidationResult title = textPolicy.validate(field + ".title", node.title);
            if (!title.allowed()) return title;
            PolicyValidationResult content = textPolicy.validate(field + ".content", node.content);
            if (!content.allowed()) return content;
        }
        if (node.type == GraphNodeType.SOURCE_EVIDENCE) {
            if (node.evidenceIds == null || node.evidenceIds.size() != 1
                    || !evidence.containsKey(node.evidenceIds.get(0))
                    || !operationEvidence.equals(node.evidenceIds)) {
                return block(field + ".evidenceIds",
                        "a source evidence node must wrap exactly one supplied evidence item");
            }
        }
        if (node.type == GraphNodeType.SELF_REPORTED_FACT
                && operationEvidence.stream().noneMatch(id -> factEligible(evidence.get(id)))) {
            return block(field + ".type",
                    "article relevance alone cannot create a personal body fact");
        }
        if (node.type == GraphNodeType.ACTION) {
            if (!ALLOWED_ACTION_TYPES.contains(node.actionType)
                    || node.actionStatus != GraphActionStatus.PENDING
                    || node.feedbackOptions == null
                    || node.feedbackOptions.size() != REQUIRED_FEEDBACK.size()
                    || !new HashSet<>(node.feedbackOptions).equals(REQUIRED_FEEDBACK)
                    || blank(node.title) || blank(node.content)) {
                return block(field, "new action must be observable, pending, feedback-enabled, and skippable");
            }
        } else if (node.actionType != null || node.actionStatus != null
                || node.dueAt != null
                || (node.feedbackOptions != null && !node.feedbackOptions.isEmpty())) {
            return block(field, "only ACTION nodes may contain action-specific fields");
        }
        return PolicyValidationResult.pass();
    }

    private PolicyValidationResult validateUpdatedNode(String field,
                                                       GraphNode current,
                                                       GraphNode updated,
                                                       List<String> operationEvidence,
                                                       Map<String, CanonicalEvidence> evidence,
                                                       GraphUpdateScope scope) {
        if (current == null || !scope.mutableNodeIds.contains(current.id)) {
            return block(field, "updated node must be mutable in the frozen target scope");
        }
        if (current.type == GraphNodeType.ACTION) {
            return validateActionFeedbackUpdate(
                    field, current, updated, operationEvidence, evidence);
        }
        if (current.type != updated.type || current.status != updated.status
                || !same(current.topicId, updated.topicId)
                || !same(current.title, updated.title)
                || updated.version != current.version + 1) {
            return block(field, "update may change content and evidence only, with version +1");
        }
        if (!same(current.domain, updated.domain)
                || !same(current.createdAt, updated.createdAt)
                || current.actionType != updated.actionType
                || current.actionStatus != updated.actionStatus
                || !same(current.dueAt, updated.dueAt)
                || !Objects.equals(current.feedbackOptions, updated.feedbackOptions)) {
            return block(field,
                    "update cannot change immutable node metadata or action fields");
        }
        Set<String> expectedEvidence = new HashSet<>(current.evidenceIds);
        expectedEvidence.addAll(operationEvidence);
        if (!sameSet(updated.evidenceIds, List.copyOf(expectedEvidence))) {
            return block(field + ".evidenceIds",
                    "updated node evidence must be the old evidence plus operation evidence");
        }
        if (updated.type != GraphNodeType.TOPIC
                && updated.type != GraphNodeType.PATTERN_HYPOTHESIS
                && updated.type != GraphNodeType.OPEN_QUESTION) {
            return block(field + ".type", "this node type is immutable in graph updates");
        }
        return textPolicy.validate(field + ".content", updated.content);
    }

    private PolicyValidationResult validateAddedEdge(String field,
                                                     GraphEdge edge,
                                                     Set<String> allNodeIds,
                                                     Map<String, GraphNode> existing,
                                                     Map<String, GraphNode> added,
                                                     Map<String, CanonicalEvidence> evidence,
                                                     String topicId) {
        if (edge.type == null || !edge.active || edge.version != 1
                || !allNodeIds.contains(edge.fromNodeId)
                || !allNodeIds.contains(edge.toNodeId)) {
            return block(field, "new edge endpoints and version are invalid");
        }
        GraphNode from = added.containsKey(edge.fromNodeId)
                ? added.get(edge.fromNodeId) : existing.get(edge.fromNodeId);
        GraphNode to = added.containsKey(edge.toNodeId)
                ? added.get(edge.toNodeId) : existing.get(edge.toNodeId);
        if (edge.type == GraphEdgeType.ABOUT
                && (from.type != GraphNodeType.SOURCE_EVIDENCE
                || to.type != GraphNodeType.TOPIC || !to.id.equals(topicId))) {
            return block(field, "ABOUT must point from source evidence to the target topic");
        }
        if (edge.type == GraphEdgeType.GROUNDS
                && (from.type != GraphNodeType.SOURCE_EVIDENCE
                || to.type == GraphNodeType.TOPIC || to.type == GraphNodeType.ACTION
                || !topicId.equals(to.topicId))) {
            return block(field, "GROUNDS must point from source evidence to a semantic node");
        }
        if (edge.type == GraphEdgeType.NEXT_STEP_FOR
                && (from.type != GraphNodeType.ACTION
                || to.type != GraphNodeType.TOPIC || !to.id.equals(topicId))) {
            return block(field, "NEXT_STEP_FOR must point from an action to the target topic");
        }
        if (edge.type == GraphEdgeType.FEEDBACK_FOR) {
            CanonicalEvidence feedback = edge.evidenceIds.size() == 1
                    ? evidence.get(edge.evidenceIds.get(0)) : null;
            if (from.type != GraphNodeType.SOURCE_EVIDENCE
                    || to.type != GraphNodeType.ACTION
                    || !topicId.equals(to.topicId)
                    || feedback == null
                    || feedback.sourceType
                    != EvidenceSourceType.ACTION_FEEDBACK
                    || !to.id.equals(feedback.relatedActionId)) {
                return block(field,
                        "FEEDBACK_FOR must link one feedback evidence item to its action");
            }
        }
        return PolicyValidationResult.pass();
    }

    private PolicyValidationResult validateActionFeedbackUpdate(
            String field,
            GraphNode current,
            GraphNode updated,
            List<String> operationEvidence,
            Map<String, CanonicalEvidence> evidence) {
        if (current.status != GraphNodeStatus.ACTIVE
                || current.actionStatus != GraphActionStatus.PENDING
                || updated.type != GraphNodeType.ACTION
                || updated.status != current.status
                || !same(current.topicId, updated.topicId)
                || !same(current.title, updated.title)
                || !same(current.content, updated.content)
                || !same(current.domain, updated.domain)
                || !same(current.createdAt, updated.createdAt)
                || current.actionType != updated.actionType
                || !same(current.dueAt, updated.dueAt)
                || !Objects.equals(current.feedbackOptions, updated.feedbackOptions)
                || updated.version != current.version + 1) {
            return block(field,
                    "feedback may only close one unchanged active pending action");
        }
        if (operationEvidence == null || operationEvidence.size() != 1) {
            return block(field + ".evidenceIds",
                    "action closure requires exactly one feedback evidence item");
        }
        CanonicalEvidence feedback = evidence.get(operationEvidence.get(0));
        if (feedback == null
                || feedback.sourceType
                != EvidenceSourceType.ACTION_FEEDBACK
                || feedback.feedbackResult == null
                || !current.id.equals(feedback.relatedActionId)) {
            return block(field + ".evidenceIds",
                    "action closure evidence must target the updated action");
        }
        GraphActionStatus expected = feedback.feedbackResult
                == GraphActionFeedbackResult.SKIPPED
                ? GraphActionStatus.SKIPPED : GraphActionStatus.COMPLETED;
        if (updated.actionStatus != expected) {
            return block(field + ".actionStatus",
                    "action status does not match the submitted feedback result");
        }
        Set<String> expectedEvidence = new HashSet<>(current.evidenceIds);
        expectedEvidence.addAll(operationEvidence);
        if (!sameSet(updated.evidenceIds, List.copyOf(expectedEvidence))) {
            return block(field + ".evidenceIds",
                    "closed action must retain old evidence and add its feedback evidence");
        }
        return PolicyValidationResult.pass();
    }

    private String edgeKey(GraphEdge edge) {
        return edge.type + "\u0000" + edge.fromNodeId + "\u0000" + edge.toNodeId;
    }

    private boolean factEligible(CanonicalEvidence value) {
        return value != null && (value.factLevel == EvidenceFactLevel.SELF_REPORTED
                || value.factLevel == EvidenceFactLevel.OBSERVED);
    }

    private boolean actionFeedback(CanonicalEvidence value) {
        return value != null
                && value.sourceType
                == EvidenceSourceType.ACTION_FEEDBACK;
    }

    private boolean semantic(GraphNodeType type) {
        return type == GraphNodeType.SELF_REPORTED_FACT
                || type == GraphNodeType.PATTERN_HYPOTHESIS
                || type == GraphNodeType.OPEN_QUESTION;
    }

    private boolean sameSet(List<String> left, List<String> right) {
        return left != null && right != null && left.size() == right.size()
                && new HashSet<>(left).equals(new HashSet<>(right));
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private PolicyValidationResult block(String field, String message) {
        return PolicyValidationResult.block(
                AgentErrorCode.POLICY_BLOCKED, field, message);
    }
}
