package com.whu.software.athena.cognitionagent.scope.policy;

import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;

import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphUpdateScopePolicyValidator {

    public PolicyValidationResult validate(PersonalCognitionGraph graph,
                                           GraphUpdateScope scope) {
        return validate(graph, scope, false);
    }

    public PolicyValidationResult validate(PersonalCognitionGraph graph,
                                           GraphUpdateScope scope,
                                           boolean allowActionMutation) {
        if (graph == null || scope == null) {
            return block("scope", "graph and scope are required");
        }
        if (!same(graph.graphId, scope.graphId)) {
            return block("scope.graphId", "scope must target the supplied graph");
        }
        if (graph.graphVersion != scope.baseGraphVersion) {
            return block("scope.baseGraphVersion",
                    "scope must use the supplied graph version");
        }
        if (scope.route == null) {
            return block("scope.route", "scope route is required");
        }
        PolicyValidationResult listPolicy = validateLists(scope);
        if (!listPolicy.allowed()) return listPolicy;

        Map<String, GraphNode> nodes = new HashMap<>();
        graph.nodes.forEach(node -> nodes.put(node.id, node));

        if (scope.route == GraphUpdateRoute.CREATE_BRANCH) {
            if (!blank(scope.targetTopicId) || blank(scope.proposedTopicTitle)) {
                return block("scope", "CREATE_BRANCH requires a title and no existing target topic");
            }
            if (!scope.readableNodeIds.isEmpty() || !scope.mutableNodeIds.isEmpty()
                    || !scope.immutableNodeIds.isEmpty()) {
                return block("scope.readableNodeIds",
                        "CREATE_BRANCH must not expose existing graph nodes");
            }
            return PolicyValidationResult.pass();
        }
        if (scope.route != GraphUpdateRoute.UPDATE_EXISTING) {
            return block("scope.route", "scope must use CREATE_BRANCH or UPDATE_EXISTING");
        }

        GraphNode topic = nodes.get(scope.targetTopicId);
        if (topic == null || topic.type != GraphNodeType.TOPIC
                || topic.status != GraphNodeStatus.ACTIVE) {
            return block("scope.targetTopicId",
                    "UPDATE_EXISTING must target an active topic");
        }

        Set<String> expectedReadable = new HashSet<>();
        Set<String> requiredMutable = new HashSet<>();
        for (GraphNode node : graph.nodes) {
            if (node.status != GraphNodeStatus.ACTIVE) continue;
            boolean inBranch = node.id.equals(topic.id) || topic.id.equals(node.topicId);
            if (!inBranch) continue;
            expectedReadable.add(node.id);
            if (standardMutable(node.type)) requiredMutable.add(node.id);
        }
        if (!expectedReadable.equals(new HashSet<>(scope.readableNodeIds))) {
            return block("scope.readableNodeIds",
                    "scope must contain exactly the active target topic branch");
        }
        if (!scope.mutableNodeIds.containsAll(requiredMutable)) {
            return block("scope.mutableNodeIds",
                    "scope must keep all editable semantic nodes mutable");
        }

        int mutableActions = 0;
        for (String id : scope.mutableNodeIds) {
            GraphNode node = nodes.get(id);
            if (node == null || !expectedReadable.contains(id)) {
                return block("scope.mutableNodeIds",
                        "mutable nodes must belong to the active target branch");
            }
            if (standardMutable(node.type)) continue;
            if (allowActionMutation && node.type == GraphNodeType.ACTION) {
                mutableActions++;
                continue;
            }
            return block("scope.mutableNodeIds",
                    "facts and source evidence are immutable; actions are mutable only for feedback");
        }
        if (mutableActions > 1) {
            return block("scope.mutableNodeIds",
                    "one feedback run may mutate only one action");
        }

        Set<String> expectedImmutable = new HashSet<>(expectedReadable);
        expectedImmutable.removeAll(scope.mutableNodeIds);
        if (!expectedImmutable.equals(new HashSet<>(scope.immutableNodeIds))) {
            return block("scope.immutableNodeIds",
                    "immutable nodes must be the remainder of the readable branch");
        }
        return PolicyValidationResult.pass();
    }

    private PolicyValidationResult validateLists(GraphUpdateScope scope) {
        List<ListField> lists = List.of(
                new ListField("scope.selectedEvidenceIds", scope.selectedEvidenceIds),
                new ListField("scope.readableNodeIds", scope.readableNodeIds),
                new ListField("scope.mutableNodeIds", scope.mutableNodeIds),
                new ListField("scope.immutableNodeIds", scope.immutableNodeIds));
        for (ListField field : lists) {
            if (field.values == null) {
                return block(field.name, "scope lists must not be null");
            }
            if (containsBlank(field.values)) {
                return block(field.name, "scope ids must not be blank");
            }
            if (field.values.size() != new HashSet<>(field.values).size()) {
                return block(field.name, "scope ids must be unique");
            }
        }
        if (scope.selectedEvidenceIds.isEmpty()) {
            return block("scope.selectedEvidenceIds", "at least one selected evidence id is required");
        }
        Set<String> overlap = new HashSet<>(scope.mutableNodeIds);
        overlap.retainAll(scope.immutableNodeIds);
        if (!overlap.isEmpty()) {
            return block("scope", "a node cannot be both mutable and immutable");
        }
        if (!new HashSet<>(scope.readableNodeIds).containsAll(scope.mutableNodeIds)
                || !new HashSet<>(scope.readableNodeIds).containsAll(scope.immutableNodeIds)) {
            return block("scope", "mutable and immutable nodes must be readable");
        }
        return PolicyValidationResult.pass();
    }

    private boolean standardMutable(GraphNodeType type) {
        return type == GraphNodeType.TOPIC
                || type == GraphNodeType.PATTERN_HYPOTHESIS
                || type == GraphNodeType.OPEN_QUESTION;
    }

    private boolean containsBlank(Collection<String> values) {
        return values.stream().anyMatch(this::blank);
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ListField(String name, List<String> values) {
    }

    private PolicyValidationResult block(String field, String message) {
        return PolicyValidationResult.block(
                AgentErrorCode.POLICY_BLOCKED, field, message);
    }
}
