package com.whu.software.athena.cognitionagent.guard.service;

import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphOperationType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphPatchOperation;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateProposal;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.graph.support.GraphContractCopier;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;

public class GraphPatchSimulationService {

    private final GraphContractCopier copier = new GraphContractCopier();
    private final GraphIntegrityValidator integrity = new GraphIntegrityValidator();

    public SimulationResult simulate(PersonalCognitionGraph source,
                                     GraphUpdateProposal proposal) {
        try {
            PersonalCognitionGraph graph = copier.graph(source);
            for (GraphPatchOperation operation : proposal.operations) {
                switch (operation.operationType) {
                    case ADD_NODE -> graph.nodes.add(copier.node(operation.node));
                    case UPDATE_NODE -> replaceNode(graph, operation.targetId, operation.node);
                    case SUPERSEDE_NODE -> supersede(graph, operation.targetId);
                    default -> {
                        // Edges are applied after all nodes so references may point to later additions.
                    }
                }
            }
            for (GraphPatchOperation operation : proposal.operations) {
                switch (operation.operationType) {
                    case ADD_EDGE -> graph.edges.add(copier.edge(operation.edge));
                    case DEACTIVATE_EDGE -> deactivateEdge(graph, operation.targetId);
                    default -> {
                        // Node operations were already applied.
                    }
                }
            }
            graph.graphVersion = source.graphVersion + 1;
            graph.updatedAt = proposal.createdAt;
            String error = integrity.validate(graph);
            return error == null
                    ? new SimulationResult(true, graph, null)
                    : new SimulationResult(false, null, error);
        } catch (RuntimeException exception) {
            return new SimulationResult(false, null, "patch simulation failed");
        }
    }

    private void replaceNode(PersonalCognitionGraph graph,
                             String targetId,
                             GraphNode replacement) {
        for (int index = 0; index < graph.nodes.size(); index++) {
            if (graph.nodes.get(index).id.equals(targetId)) {
                graph.nodes.set(index, copier.node(replacement));
                return;
            }
        }
        throw new IllegalStateException("update target does not exist");
    }

    private void supersede(PersonalCognitionGraph graph, String targetId) {
        GraphNode node = graph.nodes.stream()
                .filter(item -> item.id.equals(targetId)).findFirst().orElseThrow();
        node.status = GraphNodeStatus.SUPERSEDED;
        node.version += 1;
    }

    private void deactivateEdge(PersonalCognitionGraph graph, String targetId) {
        GraphEdge edge = graph.edges.stream()
                .filter(item -> item.id.equals(targetId)).findFirst().orElseThrow();
        edge.active = false;
        edge.version += 1;
    }

    public record SimulationResult(
            boolean valid,
            PersonalCognitionGraph simulatedGraph,
            String error
    ) {
    }
}
