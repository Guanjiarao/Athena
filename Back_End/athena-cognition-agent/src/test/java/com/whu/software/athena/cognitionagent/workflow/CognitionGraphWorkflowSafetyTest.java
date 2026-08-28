package com.whu.software.athena.cognitionagent.workflow;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticModelContext;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import com.whu.software.athena.cognitionagent.semantic.provider.GraphSemanticModelProvider;
import com.whu.software.athena.cognitionagent.semantic.provider.SemanticModelSuggestion;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class CognitionGraphWorkflowSafetyTest {

    @Autowired CognitionGraphWorkflow workflow;
    @MockBean GraphSemanticModelProvider semanticProvider;

    @Test
    void articleEvidenceCannotBecomeBodyFactThroughWorkflow() {
        GraphSemanticUpdateDraft draft = new GraphSemanticUpdateDraft();
        draft.topicTitle = "Cycle changes";
        draft.stageUnderstanding = "The marked article may be relevant but proves no body fact.";
        draft.stageUnderstandingEvidenceIds = List.of("evidence_safety");
        SemanticChange change = new SemanticChange();
        change.changeType = SemanticChangeType.ADD;
        change.nodeType = GraphNodeType.SELF_REPORTED_FACT;
        change.content = "The user definitely has this condition.";
        change.evidenceIds = List.of("evidence_safety");
        draft.changes = List.of(change);
        draft.changeSummary = "Attempted fact creation.";
        when(semanticProvider.providerName()).thenReturn("malicious-test");
        when(semanticProvider.modelName()).thenReturn("malicious-model");
        when(semanticProvider.generate(any(GraphSemanticModelContext.class)))
                .thenReturn(new SemanticModelSuggestion(
                        "malicious-test", "malicious-model", "test-prompt",
                        draft, null, null, null, null));

        GraphUpdatePreparationRequest request = request();
        long originalVersion = request.graph.graphVersion;
        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(GraphPreparationStatus.BLOCKED, response.status);
        assertEquals(PolicyResult.BLOCK, response.semanticResult.policyResult);
        assertNull(response.nextNodeId);
        assertEquals(originalVersion, request.graph.graphVersion);
    }

    @Test
    void invalidModelOutputStopsBeforePatchAssembly() {
        when(semanticProvider.providerName()).thenReturn("invalid-test");
        when(semanticProvider.modelName()).thenReturn("invalid-model");
        when(semanticProvider.generate(any(GraphSemanticModelContext.class)))
                .thenThrow(new IntentModelProviderException(
                        AgentErrorCode.MODEL_OUTPUT_INVALID,
                        "semantic model output failed schema validation", false));

        GraphUpdatePreparationResponse response = workflow.prepare(request());

        assertEquals(GraphPreparationStatus.FAILED, response.status);
        assertEquals(SchemaResult.FAIL, response.semanticResult.schemaResult);
        assertNull(response.nextNodeId);
    }

    private GraphUpdatePreparationRequest request() {
        GraphUpdatePreparationRequest value = new GraphUpdatePreparationRequest();
        value.runId = "run_safety";
        value.idempotencyKey = "clue_safety:graph-workflow-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_safety";
        value.graph = GraphTestFixtures.emptyGraph();
        value.candidates.add(GraphTestFixtures.relatedCandidate(
                "evidence_safety", "clue_safety"));
        value.suggestedTopicTitle = "Cycle changes";
        return value;
    }
}
