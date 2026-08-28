package com.whu.software.athena.cognitionagent.graph;

import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdge;
import com.whu.software.athena.cognitionagent.graph.contract.GraphEdgeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.graph.validation.GraphIntegrityValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphIntegrityValidatorTest {

    private final GraphIntegrityValidator validator = new GraphIntegrityValidator();

    @Test
    void acceptsAWellFormedActionAndNextStepEdge() {
        PersonalCognitionGraph graph = GraphTestFixtures.graphWithTwoTopics();
        graph.nodes.add(action("action_1"));
        GraphEdge edge = new GraphEdge();
        edge.id = "edge_action_topic";
        edge.type = GraphEdgeType.NEXT_STEP_FOR;
        edge.fromNodeId = "action_1";
        edge.toNodeId = "topic_mood";
        edge.evidenceIds = List.of("evidence_action");
        graph.edges.add(edge);

        assertNull(validator.validate(graph));
    }

    @Test
    void rejectsMalformedActionAndMultiplePendingActions() {
        PersonalCognitionGraph malformed = GraphTestFixtures.graphWithTwoTopics();
        GraphNode missingFeedback = action("action_bad");
        missingFeedback.feedbackOptions = List.of(GraphActionFeedbackResult.OCCURRED);
        malformed.nodes.add(missingFeedback);
        assertNotNull(validator.validate(malformed));

        PersonalCognitionGraph duplicate = GraphTestFixtures.graphWithTwoTopics();
        duplicate.nodes.add(action("action_1"));
        duplicate.nodes.add(action("action_2"));
        assertNotNull(validator.validate(duplicate));
    }

    @Test
    void rejectsInvalidEdgeDirection() {
        PersonalCognitionGraph graph = GraphTestFixtures.graphWithTwoTopics();
        GraphEdge edge = new GraphEdge();
        edge.id = "edge_bad";
        edge.type = GraphEdgeType.NEXT_STEP_FOR;
        edge.fromNodeId = "hyp_mood";
        edge.toNodeId = "topic_mood";
        graph.edges.add(edge);

        assertNotNull(validator.validate(graph));
    }

    @Test
    void rejectsUnsupportedGraphVersionAndCrossTopicEdges() {
        PersonalCognitionGraph unsupported = GraphTestFixtures.graphWithTwoTopics();
        unsupported.graphSchemaVersion = "personal-cognition-graph-v0";
        assertNotNull(validator.validate(unsupported));

        PersonalCognitionGraph crossTopic = GraphTestFixtures.graphWithTwoTopics();
        GraphNode source = new GraphNode();
        source.id = "source_cross_topic";
        source.type = GraphNodeType.SOURCE_EVIDENCE;
        source.status = GraphNodeStatus.ACTIVE;
        source.topicId = "topic_mood";
        source.evidenceIds = List.of("evidence_cross_topic");
        crossTopic.nodes.add(source);
        GraphEdge edge = new GraphEdge();
        edge.id = "edge_cross_topic";
        edge.type = GraphEdgeType.ABOUT;
        edge.fromNodeId = source.id;
        edge.toNodeId = "topic_sleep";
        edge.evidenceIds = source.evidenceIds;
        crossTopic.edges.add(edge);

        assertNotNull(validator.validate(crossTopic));
    }

    private GraphNode action(String id) {
        GraphNode action = new GraphNode();
        action.id = id;
        action.type = GraphNodeType.ACTION;
        action.status = GraphNodeStatus.ACTIVE;
        action.topicId = "topic_mood";
        action.title = "Record one body change";
        action.content = "Record timing and intensity.";
        action.evidenceIds = List.of("evidence_action");
        action.actionType = GraphActionType.RECORD_BODY;
        action.actionStatus = GraphActionStatus.PENDING;
        action.feedbackOptions = List.of(GraphActionFeedbackResult.OCCURRED,
                GraphActionFeedbackResult.NOT_OCCURRED,
                GraphActionFeedbackResult.UNCERTAIN,
                GraphActionFeedbackResult.SKIPPED);
        return action;
    }
}
