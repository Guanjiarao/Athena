package com.whu.software.athena.cognitionagent.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CluePayload;
import com.whu.software.athena.cognitionagent.intent.contract.ClueStatus;
import com.whu.software.athena.cognitionagent.intent.contract.ClueType;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.QuestionType;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.contract.TriggerType;
import com.whu.software.athena.cognitionagent.intent.observability.IntentTelemetryRecorder;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProvider;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelSuggestion;
import com.whu.software.athena.cognitionagent.intent.service.IntentClassificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IntentLocalEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixedEvaluationSetPreservesUserIntentAndDetectsEveryExpectedConflict() throws Exception {
        JsonNode cases = evaluationCases();
        AtomicInteger passedCases = new AtomicInteger();
        AtomicInteger expectedConflicts = new AtomicInteger();
        AtomicInteger detectedConflicts = new AtomicInteger();

        for (JsonNode testCase : cases) {
            IntentClassificationRequest request = request(testCase);
            IntentClassificationResponse response = new IntentClassificationService(
                    provider(testCase),
                    new IntentTelemetryRecorder(new SimpleMeterRegistry()))
                    .classify(request);

            String caseName = testCase.path("name").asText();
            assertEquals(testCase.path("expectedFinalIntent").asText(),
                    response.intent.name(), caseName + " final intent");
            assertEquals(testCase.path("expectedConflict").asBoolean(),
                    response.modelConflict, caseName + " conflict");
            assertEquals(SchemaResult.valueOf(testCase.path("expectedSchemaResult").asText()),
                    response.schemaResult, caseName + " schema result");
            assertEquals(ModelCallStatus.valueOf(testCase.path("expectedModelStatus").asText()),
                    response.observation.modelCallStatus, caseName + " model status");
            passedCases.incrementAndGet();
            if (testCase.path("expectedConflict").asBoolean()) {
                expectedConflicts.incrementAndGet();
                if (response.modelConflict) {
                    detectedConflicts.incrementAndGet();
                }
            }
        }

        assertEquals(9, passedCases.get());
        assertEquals(3, expectedConflicts.get());
        assertEquals(expectedConflicts.get(), detectedConflicts.get());
    }

    private IntentModelProvider provider(JsonNode testCase) {
        return new IntentModelProvider() {
            @Override
            public String providerName() {
                return "evaluation-fixture";
            }

            @Override
            public String modelName() {
                return "evaluation-model-v1";
            }

            @Override
            public IntentModelSuggestion suggest(IntentModelContext context) {
                if (testCase.hasNonNull("providerErrorCode")) {
                    AgentErrorCode errorCode = AgentErrorCode.valueOf(
                            testCase.path("providerErrorCode").asText());
                    throw new IntentModelProviderException(errorCode, "fixture failure", true);
                }
                return new IntentModelSuggestion(
                        providerName(),
                        modelName(),
                        AgentContract.PROMPT_VERSION,
                        ClueIntent.valueOf(testCase.path("modelIntent").asText()),
                        testCase.path("rationale").asText());
            }
        };
    }

    private IntentClassificationRequest request(JsonNode testCase) {
        ClueIntent intent = ClueIntent.valueOf(testCase.path("userIntent").asText());
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.contractVersion = AgentContract.CONTRACT_VERSION;
        request.nodeVersion = AgentContract.NODE_VERSION;
        request.runId = "run_eval_" + testCase.path("name").asText();
        request.idempotencyKey = request.runId + ":intent-evidence-v1";
        request.triggerType = TriggerType.CLUE_CREATED;
        request.contextSnapshotId = "ctx_eval_" + testCase.path("name").asText();

        request.clue = new CluePayload();
        request.clue.id = "clue_eval_" + testCase.path("name").asText();
        request.clue.type = ClueType.ARTICLE_HIGHLIGHT;
        request.clue.intent = intent;
        request.clue.relationType = testCase.hasNonNull("relationType")
                ? RelationType.valueOf(testCase.path("relationType").asText()) : null;
        request.clue.helpRequestType = HelpRequestType.valueOf(
                testCase.path("helpRequestType").asText());
        request.clue.articleId = "article_eval_1";
        request.clue.articleTitle = "Evaluation article";
        request.clue.articleType = 100;
        request.clue.selectedText = "Evaluation excerpt";
        request.clue.questionType = testCase.hasNonNull("questionType")
                ? QuestionType.valueOf(testCase.path("questionType").asText()) : null;
        request.clue.questionText = testCase.hasNonNull("questionText")
                ? testCase.path("questionText").asText() : null;
        request.clue.cycleRelation = CycleRelation.UNKNOWN;
        request.clue.source = "KNOWLEDGE_ARTICLE";
        request.clue.status = ClueStatus.PENDING;
        request.clue.originalLabel = intent.name();
        request.clue.createdAt = "2026-08-26T10:00:00+08:00";
        request.clue.updatedAt = "2026-08-26T10:00:00+08:00";
        return request;
    }

    private JsonNode evaluationCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/intent-evidence-v1/evaluation-cases.json")) {
            assertNotNull(input, "missing evaluation fixture");
            return objectMapper.readTree(input);
        }
    }
}
