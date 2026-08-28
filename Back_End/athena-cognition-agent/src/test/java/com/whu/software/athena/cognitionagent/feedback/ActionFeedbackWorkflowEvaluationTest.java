package com.whu.software.athena.cognitionagent.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowRequest;
import com.whu.software.athena.cognitionagent.feedbackworkflow.service.ActionFeedbackWorkflow;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ActionFeedbackWorkflowEvaluationTest {

    @Autowired ActionFeedbackWorkflow workflow;
    @Autowired ObjectMapper mapper;

    @TestFactory
    Stream<DynamicTest> fixedFeedbackEvaluationCasesRemainExecutable() throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/fixtures/action-feedback-workflow-v1/evaluation-cases.json"))) {
            List<EvaluationCase> cases = mapper.readValue(
                    input, new TypeReference<List<EvaluationCase>>() { });
            assertEquals(8, cases.size(), "feedback v1 baseline must contain eight cases");
            return cases.stream().map(testCase -> DynamicTest.dynamicTest(
                    testCase.caseId + ": " + testCase.description,
                    () -> evaluate(testCase)));
        }
    }

    private void evaluate(EvaluationCase testCase) {
        GraphActionFeedbackResult result = GraphActionFeedbackResult.valueOf(testCase.result);
        ActionFeedbackWorkflowRequest request = ActionFeedbackTestFixtures.request(
                testCase.caseId.toLowerCase(), result);
        switch (testCase.scenario) {
            case "DUPLICATE" -> request.existingEvidence = List.of(
                    ActionFeedbackTestFixtures.existingFeedback(
                            request.feedback.feedbackId, result));
            case "ACTION_COMPLETED" -> request.graph.nodes.stream()
                    .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                    .findFirst().orElseThrow().actionStatus = GraphActionStatus.COMPLETED;
            case "ACTION_UNKNOWN" -> request.feedback.actionId = "unknown_action";
            case "CONFLICTING_DUPLICATE" -> request.existingEvidence = List.of(
                    ActionFeedbackTestFixtures.existingFeedback(
                            request.feedback.feedbackId,
                            GraphActionFeedbackResult.OCCURRED));
            case "NORMAL" -> { }
            default -> throw new AssertionError("unsupported scenario: " + testCase.scenario);
        }

        var response = workflow.prepare(request);

        assertEquals(testCase.expectedStatus, response.status.name());
        if (testCase.expectedActionStatus != null) {
            assertEquals(testCase.expectedActionStatus,
                    response.graphPreview.nodes.stream()
                            .filter(node -> ActionFeedbackTestFixtures.ACTION_ID.equals(node.id))
                            .findFirst().orElseThrow().actionStatus.name());
        }
        if (testCase.expectedSemanticType != null) {
            assertTrue(response.proposal.operations.stream().anyMatch(operation ->
                    operation.node != null
                            && operation.node.type.name().equals(
                            testCase.expectedSemanticType)));
        }
    }

    private static class EvaluationCase {
        public String caseId;
        public String scenario;
        public String result;
        public String description;
        public String expectedStatus;
        public String expectedActionStatus;
        public String expectedSemanticType;
    }
}
