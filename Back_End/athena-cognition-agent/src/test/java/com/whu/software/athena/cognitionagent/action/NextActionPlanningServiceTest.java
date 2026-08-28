package com.whu.software.athena.cognitionagent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.software.athena.cognitionagent.action.context.NextActionContextBuilder;
import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningDecision;
import com.whu.software.athena.cognitionagent.action.contract.ActionPlanningStatus;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningResponse;
import com.whu.software.athena.cognitionagent.action.provider.GatewayNextActionModelProvider;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelOutput;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelProvider;
import com.whu.software.athena.cognitionagent.action.provider.NextActionModelSuggestion;
import com.whu.software.athena.cognitionagent.action.schema.NextActionModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.action.service.NextActionPlanningService;
import com.whu.software.athena.cognitionagent.common.observability.WorkflowTelemetryRecorder;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.model.MockModelGateway;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextActionPlanningServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsOneFeedbackEnabledObservationAction() {
        NextActionPlanningResponse response = service(provider()).plan(createRequest());

        assertEquals(ActionPlanningStatus.READY, response.status);
        assertEquals(ActionPlanningDecision.CREATE_NEW, response.plan.decision);
        assertEquals(GraphActionType.RECORD_BODY, response.plan.actionType);
        assertEquals(Set.of(GraphActionFeedbackResult.OCCURRED,
                        GraphActionFeedbackResult.NOT_OCCURRED,
                        GraphActionFeedbackResult.UNCERTAIN,
                        GraphActionFeedbackResult.SKIPPED),
                Set.copyOf(response.plan.feedbackOptions));
        assertNull(response.plan.dueAt);
    }

    @Test
    void deterministicallyKeepsExistingPendingActionWithoutCallingModel() {
        NextActionPlanningRequest request = createRequest();
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.scope.route = GraphUpdateRoute.UPDATE_EXISTING;
        request.scope.targetTopicId = "topic_mood";
        request.scope.baseGraphVersion = request.graph.graphVersion;
        GraphNode action = pendingAction();
        request.graph.nodes.add(action);
        request.scope.readableNodeIds = List.of(
                "topic_mood", "hyp_mood", "action_existing");
        request.scope.mutableNodeIds = List.of("topic_mood", "hyp_mood");
        request.scope.immutableNodeIds = List.of("action_existing");
        NextActionModelProvider forbiddenProvider = new NextActionModelProvider() {
            @Override public String providerName() { return "must-not-run"; }
            @Override public String modelName() { return "must-not-run"; }
            @Override public NextActionModelSuggestion plan(
                    com.whu.software.athena.cognitionagent.action.context.NextActionModelContext context) {
                throw new AssertionError("model must not run when a pending action exists");
            }
        };

        NextActionPlanningResponse response = service(forbiddenProvider).plan(request);

        assertEquals(ActionPlanningStatus.READY, response.status);
        assertEquals(ActionPlanningDecision.KEEP_EXISTING, response.plan.decision);
        assertEquals("action_existing", response.plan.existingActionNodeId);
        assertEquals("NOT_ATTEMPTED", response.observation.modelCallStatus.name());
    }

    @Test
    void modelContextContainsOnlyTheNodeSixWhitelist() throws Exception {
        NextActionPlanningRequest request = createRequest();
        NextActionContextBuilder builder = new NextActionContextBuilder();
        String json = mapper.writeValueAsString(
                builder.buildModelContext(builder.build(request)));

        assertTrue(json.contains("allowedActionTypes"));
        assertTrue(json.contains("stageUnderstanding"));
        assertFalse(json.contains("graphId"));
        assertFalse(json.contains("graphVersion"));
        assertFalse(json.contains("contentFingerprint"));
        assertFalse(json.contains("sourceId"));
    }

    @Test
    void blocksOutOfScopeActionAndRejectsExtraModelFields() {
        NextActionModelProvider malicious = new NextActionModelProvider() {
            @Override public String providerName() { return "malicious"; }
            @Override public String modelName() { return "malicious"; }
            @Override public NextActionModelSuggestion plan(
                    com.whu.software.athena.cognitionagent.action.context.NextActionModelContext context) {
                NextActionModelOutput output = new NextActionModelOutput();
                output.actionType = GraphActionType.READ_CONTENT;
                output.title = "Read more";
                output.description = "Read another article.";
                output.evidenceIds = List.of("evidence_action");
                output.rationale = "More content may help.";
                return new NextActionModelSuggestion("malicious", "malicious", "test",
                        output, null, null, null, null);
            }
        };
        NextActionPlanningResponse response = service(malicious).plan(createRequest());
        ObjectNode invalid = mapper.createObjectNode();
        invalid.put("actionType", "RECORD_BODY");
        invalid.put("title", "Record once");
        invalid.put("description", "Record timing and intensity.");
        invalid.putArray("evidenceIds").add("evidence_action");
        invalid.put("rationale", "Observe once.");
        invalid.put("databaseAction", "INSERT");

        assertEquals(ActionPlanningStatus.BLOCKED, response.status);
        assertFalse(new NextActionModelOutputSchemaValidator().validate(invalid).valid());
    }

    private NextActionPlanningService service(NextActionModelProvider provider) {
        return new NextActionPlanningService(provider,
                new WorkflowTelemetryRecorder(new SimpleMeterRegistry()));
    }

    private NextActionModelProvider provider() {
        return new GatewayNextActionModelProvider(
                new MockModelGateway(mapper), mapper);
    }

    private NextActionPlanningRequest createRequest() {
        NextActionPlanningRequest request = new NextActionPlanningRequest();
        request.runId = "run_action";
        request.idempotencyKey = "action-idempotency";
        request.triggerType = GraphTriggerType.USER_REQUEST;
        request.contextSnapshotId = "ctx_action";
        request.graph = GraphTestFixtures.emptyGraph();
        request.evidence = List.of(GraphTestFixtures.declaredEvidence("evidence_action"));
        request.scope = new GraphUpdateScope();
        request.scope.graphId = request.graph.graphId;
        request.scope.baseGraphVersion = request.graph.graphVersion;
        request.scope.route = GraphUpdateRoute.CREATE_BRANCH;
        request.scope.proposedTopicTitle = "Cycle changes";
        request.scope.selectedEvidenceIds = List.of("evidence_action");
        request.semanticDraft = new GraphSemanticUpdateDraft();
        request.semanticDraft.topicTitle = "Cycle changes";
        request.semanticDraft.stageUnderstanding = "One relevant clue needs observation.";
        request.semanticDraft.stageUnderstandingEvidenceIds = List.of("evidence_action");
        SemanticChange question = new SemanticChange();
        question.changeType = SemanticChangeType.ADD;
        question.nodeType = GraphNodeType.OPEN_QUESTION;
        question.content = "Will this change happen again?";
        question.evidenceIds = List.of("evidence_action");
        request.semanticDraft.changes = List.of(question);
        request.semanticDraft.changeSummary = "Add one open question.";
        return request;
    }

    private GraphNode pendingAction() {
        GraphNode action = new GraphNode();
        action.id = "action_existing";
        action.type = GraphNodeType.ACTION;
        action.status = GraphNodeStatus.ACTIVE;
        action.topicId = "topic_mood";
        action.title = "Record one body change";
        action.content = "Record timing and intensity.";
        action.actionType = GraphActionType.RECORD_BODY;
        action.actionStatus = GraphActionStatus.PENDING;
        action.feedbackOptions = List.of(GraphActionFeedbackResult.OCCURRED,
                GraphActionFeedbackResult.NOT_OCCURRED,
                GraphActionFeedbackResult.UNCERTAIN,
                GraphActionFeedbackResult.SKIPPED);
        action.evidenceIds = List.of("old_action_evidence");
        return action;
    }
}
