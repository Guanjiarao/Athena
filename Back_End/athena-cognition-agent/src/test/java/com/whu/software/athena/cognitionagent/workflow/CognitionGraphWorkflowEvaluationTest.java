package com.whu.software.athena.cognitionagent.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContext;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContextBuilder;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import com.whu.software.athena.cognitionagent.semantic.policy.GraphSemanticPolicyValidator;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphPreparationStatus;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CognitionGraphWorkflowEvaluationTest {

    @Autowired CognitionGraphWorkflow workflow;
    @Autowired ObjectMapper mapper;

    @TestFactory
    Stream<DynamicTest> fixedEvaluationCasesRemainExecutable() throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/fixtures/cognition-graph-workflow-v1/evaluation-cases.json"))) {
            List<EvaluationCase> cases = mapper.readValue(
                    input, new TypeReference<List<EvaluationCase>>() { });
            assertEquals(9, cases.size(), "The v1 baseline must contain exactly nine cases");
            return cases.stream().map(testCase -> DynamicTest.dynamicTest(
                    testCase.caseId + ": " + testCase.description,
                    () -> evaluate(testCase)));
        }
    }

    private void evaluate(EvaluationCase testCase) {
        switch (testCase.scenario) {
            case "EMPTY_GRAPH" -> evaluateEmptyGraph(testCase);
            case "EXISTING_BRANCH" -> evaluateExistingBranch(testCase);
            case "DUPLICATE_EVIDENCE" -> evaluateDuplicateEvidence(testCase);
            case "USER_TARGET" -> evaluateUserTarget(testCase);
            case "AMBIGUOUS_TARGET" -> evaluateAmbiguousTarget(testCase);
            case "CONTEXT_ISOLATION" -> evaluateContextIsolation(testCase);
            case "FACT_POLICY_BLOCK" -> evaluateFactPolicyBlock(testCase);
            case "SOURCE_CONFLICT" -> evaluateSourceConflict(testCase);
            case "REPEATED_BODY_OBSERVATION" -> evaluateRepeatedBodyObservation(testCase);
            default -> throw new AssertionError("Unsupported evaluation scenario: "
                    + testCase.scenario);
        }
    }

    private void evaluateEmptyGraph(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = request(testCase.caseId);
        request.graph = GraphTestFixtures.emptyGraph();
        request.suggestedTopicTitle = "Cycle changes";

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        assertEquals(testCase.expectedRoute, response.targetResult.route.name());
    }

    private void evaluateExistingBranch(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = existingBranchRequest(testCase.caseId);

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        assertEquals(testCase.expectedRoute, response.targetResult.route.name());
    }

    private void evaluateDuplicateEvidence(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = existingBranchRequest(testCase.caseId);
        GraphUpdatePreparationResponse first = workflow.prepare(request);
        request.existingEvidence = first.evidenceResult.acceptedEvidence;

        GraphUpdatePreparationResponse duplicate = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, duplicate.status.name());
    }

    private void evaluateUserTarget(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = existingBranchRequest(testCase.caseId);
        request.userSelectedTopicId = "topic_sleep";

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals("topic_sleep", response.targetResult.targetTopicId);
        assertEquals(testCase.expectedDecisionSource,
                response.targetResult.decisionSource.name());
        assertEquals(DecisionSource.USER_DECLARED, response.targetResult.decisionSource);
    }

    private void evaluateAmbiguousTarget(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = request(testCase.caseId);
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.suggestedTopicTitle = "Unmatched topic";

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        assertEquals(GraphUpdateRoute.NEEDS_CONFIRMATION, response.targetResult.route);
    }

    private void evaluateContextIsolation(EvaluationCase testCase) {
        GraphUpdatePreparationResponse response = workflow.prepare(
                existingBranchRequest(testCase.caseId));

        long leaked = response.scopeResult.scope.readableNodeIds.stream()
                .filter(id -> id.equals("topic_sleep") || id.equals("hyp_sleep"))
                .count();
        assertEquals(testCase.expectedLeakedNodeCount, leaked);
    }

    private void evaluateFactPolicyBlock(EvaluationCase testCase) {
        GraphUpdatePreparationResponse prepared = workflow.prepare(
                existingBranchRequest(testCase.caseId));
        GraphSemanticUpdateRequest request = new GraphSemanticUpdateRequest();
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.evidence = prepared.evidenceResult.acceptedEvidence;
        request.scope = prepared.scopeResult.scope;
        GraphSemanticContext context = new GraphSemanticContextBuilder().build(request);

        SemanticChange change = new SemanticChange();
        change.changeType = SemanticChangeType.ADD;
        change.nodeType = GraphNodeType.SELF_REPORTED_FACT;
        change.content = "The user has this condition.";
        change.evidenceIds = List.of("evidence_" + testCase.caseId.toLowerCase());
        GraphSemanticUpdateDraft draft = new GraphSemanticUpdateDraft();
        draft.topicTitle = prepared.scopeResult.scope.proposedTopicTitle;
        draft.stageUnderstanding = "The article was marked as relevant.";
        draft.stageUnderstandingEvidenceIds = change.evidenceIds;
        draft.changes = List.of(change);
        draft.changeSummary = "Attempt to create a body fact from article relevance.";

        assertEquals(testCase.expectedPolicyResult,
                new GraphSemanticPolicyValidator().validate(context, draft).allowed()
                        ? "PASS" : "BLOCK");
    }

    private void evaluateSourceConflict(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = request(testCase.caseId);
        request.graph = GraphTestFixtures.emptyGraph();
        request.suggestedTopicTitle = "Cycle changes";
        CanonicalEvidence existing = GraphTestFixtures.declaredEvidence("existing_conflict");
        existing.sourceId = "clue_" + testCase.caseId.toLowerCase();
        existing.contentFingerprint = "fingerprint_from_previous_payload";
        request.existingEvidence = List.of(existing);

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        assertEquals(testCase.expectedErrorCode, response.error.code.name());
    }

    private void evaluateRepeatedBodyObservation(EvaluationCase testCase) {
        GraphUpdatePreparationRequest request = request(testCase.caseId);
        request.graph = GraphTestFixtures.emptyGraph();
        request.suggestedTopicTitle = "重复的身体观察";
        request.candidates = List.of(
                bodyObservation("body_event_1", "record_1", "2026-08-20T08:00:00+08:00"),
                bodyObservation("body_event_2", "record_2", "2026-08-27T08:00:00+08:00"));

        GraphUpdatePreparationResponse response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        assertEquals(testCase.expectedAcceptedCount,
                response.evidenceResult.acceptedEvidence.size());
    }

    private EvidenceCandidate bodyObservation(String evidenceId,
                                              String sourceId,
                                              String occurredAt) {
        EvidenceCandidate value = new EvidenceCandidate();
        value.evidenceId = evidenceId;
        value.sourceType = EvidenceSourceType.BODY_RECORD;
        value.sourceId = sourceId;
        value.intent = ClueIntent.RELATED;
        value.relationType = RelationType.CURRENT;
        value.summary = "The same mild discomfort was recorded.";
        value.occurredAt = occurredAt;
        value.cycleRelation = CycleRelation.UNKNOWN;
        value.severity = 2;
        value.resolved = false;
        return value;
    }

    private GraphUpdatePreparationRequest existingBranchRequest(String caseId) {
        GraphUpdatePreparationRequest request = request(caseId);
        request.graph = GraphTestFixtures.graphWithTwoTopics();
        request.suggestedTopicTitle = request.graph.nodes.stream()
                .filter(node -> "topic_mood".equals(node.id))
                .map(node -> node.title)
                .findFirst()
                .orElseThrow();
        return request;
    }

    private GraphUpdatePreparationRequest request(String caseId) {
        String suffix = caseId.toLowerCase();
        GraphUpdatePreparationRequest value = new GraphUpdatePreparationRequest();
        value.runId = "run_" + suffix;
        value.idempotencyKey = "clue_" + suffix + ":graph-workflow-v1";
        value.triggerType = GraphTriggerType.USER_REQUEST;
        value.contextSnapshotId = "ctx_" + suffix;
        value.candidates.add(GraphTestFixtures.relatedCandidate(
                "evidence_" + suffix, "clue_" + suffix));
        return value;
    }

    private static class EvaluationCase {
        public String caseId;
        public String scenario;
        public String description;
        public String expectedStatus;
        public String expectedRoute;
        public String expectedDecisionSource;
        public Long expectedLeakedNodeCount;
        public String expectedPolicyResult;
        public String expectedErrorCode;
        public Integer expectedAcceptedCount;
    }
}
