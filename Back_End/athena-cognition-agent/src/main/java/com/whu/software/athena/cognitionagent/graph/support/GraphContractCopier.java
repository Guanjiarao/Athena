package com.whu.software.athena.cognitionagent.graph.support;

import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphPatchOperation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;

import java.util.ArrayList;

public class GraphContractCopier {

    public PersonalCognitionGraph graph(PersonalCognitionGraph source) {
        PersonalCognitionGraph target = new PersonalCognitionGraph();
        target.graphSchemaVersion = source.graphSchemaVersion;
        target.graphId = source.graphId;
        target.graphVersion = source.graphVersion;
        source.nodes.forEach(node -> target.nodes.add(node(node)));
        source.edges.forEach(edge -> target.edges.add(edge(edge)));
        target.updatedAt = source.updatedAt;
        return target;
    }

    public GraphNode node(GraphNode source) {
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

    public GraphEdge edge(GraphEdge source) {
        GraphEdge target = new GraphEdge();
        target.id = source.id;
        target.type = source.type;
        target.fromNodeId = source.fromNodeId;
        target.toNodeId = source.toNodeId;
        target.evidenceIds = source.evidenceIds == null
                ? new ArrayList<>() : new ArrayList<>(source.evidenceIds);
        target.active = source.active;
        target.createdAt = source.createdAt;
        target.updatedAt = source.updatedAt;
        target.version = source.version;
        return target;
    }

    public GraphUpdateProposal proposal(GraphUpdateProposal source) {
        GraphUpdateProposal target = new GraphUpdateProposal();
        target.proposalSchemaVersion = source.proposalSchemaVersion;
        target.proposalId = source.proposalId;
        target.graphId = source.graphId;
        target.baseGraphVersion = source.baseGraphVersion;
        target.status = source.status;
        target.route = source.route;
        target.targetTopicId = source.targetTopicId;
        target.evidenceIds = source.evidenceIds == null
                ? new ArrayList<>() : new ArrayList<>(source.evidenceIds);
        if (source.operations != null) {
            source.operations.forEach(operation -> target.operations.add(operation(operation)));
        }
        target.changeSummary = source.changeSummary;
        target.requiresUserConfirmation = source.requiresUserConfirmation;
        target.workflowVersion = source.workflowVersion;
        target.createdAt = source.createdAt;
        return target;
    }

    public GraphPatchOperation operation(GraphPatchOperation source) {
        GraphPatchOperation target = new GraphPatchOperation();
        target.operationType = source.operationType;
        target.targetId = source.targetId;
        target.node = source.node == null ? null : node(source.node);
        target.edge = source.edge == null ? null : edge(source.edge);
        target.supersededByNodeId = source.supersededByNodeId;
        target.evidenceIds = source.evidenceIds == null
                ? new ArrayList<>() : new ArrayList<>(source.evidenceIds);
        target.reason = source.reason;
        return target;
    }
}
