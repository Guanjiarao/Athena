package com.whu.software.athena.cognitionagent.intent;

import com.whu.software.athena.cognitionagent.intent.context.IntentContextBuilder;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContextBuilder;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CluePayload;
import com.whu.software.athena.cognitionagent.intent.contract.ClueStatus;
import com.whu.software.athena.cognitionagent.intent.contract.ClueType;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.HelpRequestType;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationStatus;
import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.QuestionType;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;
import com.whu.software.athena.cognitionagent.intent.contract.TriggerType;
import com.whu.software.athena.cognitionagent.intent.observability.IntentTelemetryRecorder;
import com.whu.software.athena.cognitionagent.intent.policy.IntentModelPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.policy.IntentPolicyValidator;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProvider;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelSuggestion;
import com.whu.software.athena.cognitionagent.intent.schema.IntentModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.intent.service.IntentClassificationService;
import com.whu.software.athena.cognitionagent.intent.service.RuleFirstIntentClassifier;
import com.whu.software.athena.cognitionagent.intent.validation.IntentRequestValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentWorkflowClosureTest {

    @Test
    void matchingSuggestionCompletesSchemaPolicyAndObservability() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IntentClassificationResponse response = service(provider(
                new IntentModelSuggestion("test", "test-model", AgentContract.PROMPT_VERSION,
                        ClueIntent.QUESTION, "The user explicitly asks a question.",
                        100, 20, 120, 0.001)), registry)
                .classify(questionRequest());

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(ClueIntent.QUESTION, response.intent);
        assertEquals(DecisionSource.USER_DECLARED, response.decisionSource);
        assertEquals(SchemaResult.PASS, response.schemaResult);
        assertEquals(PolicyResult.PASS, response.policyResult);
        assertNotNull(response.modelSuggestion);
        assertFalse(response.modelConflict);
        assertEquals(ModelCallStatus.SUCCEEDED, response.observation.modelCallStatus);
        assertEquals(PolicyResult.PASS, response.observation.modelPolicyResult);
        assertEquals(120, response.observation.totalTokens);
        assertEquals(0.001, response.observation.estimatedCost);
        assertEquals(AgentContract.WORKFLOW_VERSION, response.observation.workflowVersion);
        assertEquals("ctx_closure_1", response.observation.contextSnapshotId);
        assertEquals(ClueIntent.QUESTION, response.observation.userDecision);
        assertTrue(response.observation.latencyMs >= 0);
        assertTrue(response.observation.nodes.size() >= 6);
        assertEquals(1.0, registry.get("athena.agent.node.runs").counter().count());
        assertEquals(1, registry.get("athena.agent.node.duration").timer().count());
    }

    @Test
    void conflictingModelSuggestionIsRecordedButCannotOverrideUserChoice() {
        IntentClassificationResponse response = service(provider(
                new IntentModelSuggestion("test", "test-model", AgentContract.PROMPT_VERSION,
                        ClueIntent.RELATED, "The marked text appears personally relevant.")),
                new SimpleMeterRegistry()).classify(questionRequest());

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(ClueIntent.QUESTION, response.intent);
        assertEquals(DecisionSource.USER_DECLARED, response.decisionSource);
        assertEquals(ClueIntent.RELATED, response.modelSuggestion.suggestedIntent);
        assertTrue(response.modelConflict);
        assertTrue(response.observation.modelConflict);
        assertEquals(PolicyResult.PASS, response.policyResult);
    }

    @Test
    void invalidModelShapeIsRejectedWithoutBreakingDeterministicResult() {
        IntentClassificationResponse response = service(provider(
                new IntentModelSuggestion("test", "test-model", AgentContract.PROMPT_VERSION,
                        ClueIntent.QUESTION, "")), new SimpleMeterRegistry())
                .classify(questionRequest());

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(ClueIntent.QUESTION, response.intent);
        assertEquals(SchemaResult.FAIL, response.schemaResult);
        assertEquals(ModelCallStatus.REJECTED, response.observation.modelCallStatus);
        assertEquals(AgentErrorCode.MODEL_OUTPUT_INVALID.name(), response.observation.modelErrorCode);
        assertNull(response.modelSuggestion);
        assertNull(response.error);
    }

    @Test
    void unsafeModelRationaleIsBlockedWithoutBecomingBusinessOutput() {
        IntentClassificationResponse response = service(provider(
                new IntentModelSuggestion("test", "test-model", AgentContract.PROMPT_VERSION,
                        ClueIntent.RELATED, "用户患有痛经")), new SimpleMeterRegistry())
                .classify(questionRequest());

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(SchemaResult.PASS, response.schemaResult);
        assertEquals(ModelCallStatus.REJECTED, response.observation.modelCallStatus);
        assertEquals(PolicyResult.BLOCK, response.observation.modelPolicyResult);
        assertEquals(AgentErrorCode.POLICY_BLOCKED.name(), response.observation.modelErrorCode);
        assertNull(response.modelSuggestion);
        assertFalse(response.modelConflict);
    }

    @Test
    void providerFailureIsObservableAndFallsBackToRules() {
        IntentModelProvider failingProvider = new IntentModelProvider() {
            @Override
            public String providerName() {
                return "failing-test";
            }

            @Override
            public String modelName() {
                return "failing-model";
            }

            @Override
            public IntentModelSuggestion suggest(IntentModelContext context) {
                throw new IntentModelProviderException(
                        AgentErrorCode.MODEL_TIMEOUT, "timeout", true);
            }
        };

        IntentClassificationResponse response = service(failingProvider, new SimpleMeterRegistry())
                .classify(questionRequest());

        assertEquals(IntentClassificationStatus.SUCCEEDED, response.status);
        assertEquals(ClueIntent.QUESTION, response.intent);
        assertEquals(SchemaResult.NOT_RUN, response.schemaResult);
        assertEquals(ModelCallStatus.FAILED, response.observation.modelCallStatus);
        assertEquals(AgentErrorCode.MODEL_TIMEOUT.name(), response.observation.modelErrorCode);
        assertNull(response.modelSuggestion);
    }

    @Test
    void invalidBusinessInputIsRejectedBeforeModelCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        IntentModelProvider countingProvider = new IntentModelProvider() {
            @Override
            public String providerName() {
                return "counting-test";
            }

            @Override
            public String modelName() {
                return "counting-model";
            }

            @Override
            public IntentModelSuggestion suggest(IntentModelContext context) {
                modelCalls.incrementAndGet();
                return new IntentModelSuggestion("counting-test", "counting-model",
                        AgentContract.PROMPT_VERSION, ClueIntent.QUESTION, "Valid rationale");
            }
        };
        IntentClassificationRequest request = questionRequest();
        request.clue.questionText = null;

        IntentClassificationResponse response = service(countingProvider, new SimpleMeterRegistry())
                .classify(request);

        assertEquals(IntentClassificationStatus.REJECTED, response.status);
        assertEquals(0, modelCalls.get());
        assertEquals(ModelCallStatus.NOT_ATTEMPTED, response.observation.modelCallStatus);
        assertEquals(SchemaResult.NOT_RUN, response.schemaResult);
    }

    private IntentClassificationService service(IntentModelProvider provider,
                                                SimpleMeterRegistry registry) {
        return new IntentClassificationService(
                new IntentRequestValidator(),
                new IntentContextBuilder(),
                new IntentModelContextBuilder(),
                new RuleFirstIntentClassifier(),
                new IntentPolicyValidator(),
                new IntentModelOutputSchemaValidator(),
                new IntentModelPolicyValidator(),
                provider,
                new IntentTelemetryRecorder(registry));
    }

    private IntentModelProvider provider(IntentModelSuggestion suggestion) {
        return new IntentModelProvider() {
            @Override
            public String providerName() {
                return suggestion.provider();
            }

            @Override
            public String modelName() {
                return suggestion.modelName();
            }

            @Override
            public IntentModelSuggestion suggest(IntentModelContext context) {
                return suggestion;
            }
        };
    }

    private IntentClassificationRequest questionRequest() {
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.contractVersion = AgentContract.CONTRACT_VERSION;
        request.nodeVersion = AgentContract.NODE_VERSION;
        request.runId = "run_closure_1";
        request.idempotencyKey = "clue_closure_1:intent-evidence-v1";
        request.triggerType = TriggerType.CLUE_CREATED;
        request.contextSnapshotId = "ctx_closure_1";

        request.clue = new CluePayload();
        request.clue.id = "clue_closure_1";
        request.clue.type = ClueType.ARTICLE_HIGHLIGHT;
        request.clue.intent = ClueIntent.QUESTION;
        request.clue.helpRequestType = HelpRequestType.KNOWLEDGE;
        request.clue.articleId = "article_1";
        request.clue.articleTitle = "Cycle changes";
        request.clue.articleType = 100;
        request.clue.selectedText = "Selected article text";
        request.clue.questionType = QuestionType.IS_COMMON;
        request.clue.questionText = "Is this common?";
        request.clue.cycleRelation = CycleRelation.NO_RELATION;
        request.clue.source = "KNOWLEDGE_ARTICLE";
        request.clue.status = ClueStatus.PENDING;
        request.clue.originalLabel = "I have a question";
        request.clue.createdAt = "2026-08-26T10:00:00+08:00";
        request.clue.updatedAt = "2026-08-26T10:00:00+08:00";
        return request;
    }
}
