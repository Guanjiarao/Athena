package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.action.context.NextActionModelContext;
import com.whu.software.athena.cognitionagent.action.provider.GatewayNextActionModelProvider;
import com.whu.software.athena.cognitionagent.common.text.UserVisibleTextPolicy;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticModelContext;
import com.whu.software.athena.cognitionagent.semantic.provider.GatewayGraphSemanticModelProvider;
import com.whu.software.athena.cognitionagent.semantic.provider.SemanticModelSuggestion;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetModelContext;
import com.whu.software.athena.cognitionagent.target.provider.GatewayGraphTargetModelProvider;
import com.whu.software.athena.cognitionagent.target.provider.TargetModelSuggestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every user-facing model provider must reject non-Chinese user-visible text,
 * retry the model call exactly once with a correction hint, and fail the task
 * (MODEL_OUTPUT_INVALID, not retryable) when the retry stays non-Chinese.
 */
class GatewayUserVisibleTextPolicyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void semanticProviderRetriesOnceThenAcceptsChineseAnswer() {
        ScriptedGateway gateway = new ScriptedGateway(
                semanticOutput("Mood swings under observation notes",
                        "Current stage understanding in English only."),
                semanticOutput("情绪变化", "当前只有一条线索，仍需继续观察。"));
        GatewayGraphSemanticModelProvider provider =
                new GatewayGraphSemanticModelProvider(gateway, mapper);

        SemanticModelSuggestion suggestion = provider.generate(semanticContext());

        assertEquals("情绪变化", suggestion.draft().topicTitle);
        assertEquals(2, gateway.requests.size());
        assertTrue(gateway.requests.get(1).userPrompt()
                .contains(UserVisibleTextPolicy.CORRECTION_HINT.trim()));
    }

    @Test
    void semanticProviderFailsWhenRetryStaysEnglish() {
        ScriptedGateway gateway = new ScriptedGateway(
                semanticOutput("Mood swings under observation notes",
                        "Current stage understanding in English only."),
                semanticOutput("Still an English topic title here",
                        "And still an English stage understanding."));
        GatewayGraphSemanticModelProvider provider =
                new GatewayGraphSemanticModelProvider(gateway, mapper);

        IntentModelProviderException exception = assertThrows(
                IntentModelProviderException.class,
                () -> provider.generate(semanticContext()));

        assertEquals(AgentErrorCode.MODEL_OUTPUT_INVALID, exception.errorCode());
        assertFalse(exception.retryable());
        assertEquals(2, gateway.requests.size());
    }

    @Test
    void actionProviderRetriesOnceThenAcceptsChineseAnswer() {
        ScriptedGateway gateway = new ScriptedGateway(
                actionOutput("Record one related body change every day",
                        "记录发生时间和强度。", "一次观察即可回答这个待确认问题。"),
                actionOutput("记录一次相关的身体变化",
                        "记录发生时间和强度。", "一次观察即可回答这个待确认问题。"));
        GatewayNextActionModelProvider provider =
                new GatewayNextActionModelProvider(gateway, mapper);

        var suggestion = provider.plan(actionContext());

        assertEquals("记录一次相关的身体变化", suggestion.output().title);
        assertEquals(2, gateway.requests.size());
    }

    @Test
    void actionProviderFailsWhenRetryStaysEnglish() {
        ScriptedGateway gateway = new ScriptedGateway(
                actionOutput("Record one related body change every day",
                        "记录发生时间和强度。", "一次观察即可回答这个待确认问题。"),
                actionOutput("Record one related body change every day",
                        "记录发生时间和强度。", "一次观察即可回答这个待确认问题。"));
        GatewayNextActionModelProvider provider =
                new GatewayNextActionModelProvider(gateway, mapper);

        IntentModelProviderException exception = assertThrows(
                IntentModelProviderException.class,
                () -> provider.plan(actionContext()));

        assertEquals(AgentErrorCode.MODEL_OUTPUT_INVALID, exception.errorCode());
        assertFalse(exception.retryable());
    }

    @Test
    void targetProviderRetriesOnceThenAcceptsChineseAnswer() {
        ScriptedGateway gateway = new ScriptedGateway(
                targetOutput("Unsorted English observations for the user",
                        "The router cannot match any existing topic candidate."),
                targetOutput("待理解内容", "无法匹配既有主题，交给用户确认。"));
        GatewayGraphTargetModelProvider provider =
                new GatewayGraphTargetModelProvider(gateway, mapper);

        TargetModelSuggestion suggestion = provider.resolve(targetContext());

        assertEquals("待理解内容", suggestion.suggestedTopicTitle());
        assertEquals(2, gateway.requests.size());
    }

    @Test
    void targetProviderFailsWhenRetryStaysEnglish() {
        ScriptedGateway gateway = new ScriptedGateway(
                targetOutput("待理解内容",
                        "The router cannot match any existing topic candidate."),
                targetOutput("待理解内容",
                        "The router still answers the rationale in English."));
        GatewayGraphTargetModelProvider provider =
                new GatewayGraphTargetModelProvider(gateway, mapper);

        IntentModelProviderException exception = assertThrows(
                IntentModelProviderException.class,
                () -> provider.resolve(targetContext()));

        assertEquals(AgentErrorCode.MODEL_OUTPUT_INVALID, exception.errorCode());
        assertFalse(exception.retryable());
    }

    @Test
    void chineseFirstAnswerDoesNotRetry() {
        ScriptedGateway gateway = new ScriptedGateway(
                actionOutput("记录一次相关的身体变化",
                        "记录发生时间和强度。", "一次观察即可回答这个待确认问题。"));
        GatewayNextActionModelProvider provider =
                new GatewayNextActionModelProvider(gateway, mapper);

        provider.plan(actionContext());

        assertEquals(1, gateway.requests.size());
    }

    private GraphSemanticModelContext semanticContext() {
        return new GraphSemanticModelContext(GraphUpdateRoute.UPDATE_EXISTING,
                "topic_1", "情绪变化", List.of(), List.of());
    }

    private NextActionModelContext actionContext() {
        return new NextActionModelContext(GraphUpdateRoute.UPDATE_EXISTING,
                "情绪变化", "当前只有一条线索。", List.of(), List.of(),
                List.of(GraphActionType.RECORD_BODY));
    }

    private GraphTargetModelContext targetContext() {
        return new GraphTargetModelContext("待理解内容", List.of(), List.of());
    }

    private JsonNode semanticOutput(String topicTitle, String stageUnderstanding) {
        var output = mapper.createObjectNode();
        output.put("topicTitle", topicTitle);
        output.put("stageUnderstanding", stageUnderstanding);
        output.putArray("stageUnderstandingEvidenceIds").add("ev1");
        var change = output.putArray("changes").addObject();
        change.put("changeType", "ADD");
        change.put("nodeType", "OPEN_QUESTION");
        change.putNull("targetNodeId");
        change.put("content", "这个变化会重复出现吗？");
        change.putArray("evidenceIds").add("ev1");
        output.put("changeSummary", "新增一条待确认线索。");
        return output;
    }

    private JsonNode actionOutput(String title, String description, String rationale) {
        var output = mapper.createObjectNode();
        output.put("actionType", "RECORD_BODY");
        output.put("title", title);
        output.put("description", description);
        output.putArray("evidenceIds").add("ev1");
        output.put("rationale", rationale);
        return output;
    }

    private JsonNode targetOutput(String suggestedTopicTitle, String rationale) {
        var output = mapper.createObjectNode();
        output.put("route", "NEEDS_CONFIRMATION");
        output.putNull("matchedTopicId");
        output.put("suggestedTopicTitle", suggestedTopicTitle);
        output.put("rationale", rationale);
        return output;
    }

    /** Deterministic gateway that returns scripted outputs in call order. */
    private static final class ScriptedGateway implements ModelGateway {

        private final Deque<JsonNode> outputs = new ArrayDeque<>();
        private final List<ModelRequest> requests = new ArrayList<>();

        private ScriptedGateway(JsonNode... outputs) {
            this.outputs.addAll(List.of(outputs));
        }

        @Override public String providerName() { return "scripted"; }
        @Override public String modelName() { return "scripted-v1"; }

        @Override
        public ModelResponse complete(ModelRequest request) {
            requests.add(request);
            return new ModelResponse(providerName(), modelName(), outputs.poll(),
                    null, null, null, null);
        }
    }
}
