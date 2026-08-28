package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;

/**
 * Deterministic local replacement for the shared model transport.
 * It is deliberately simple and exists only for offline workflow tests.
 */
public class MockModelGateway implements ModelGateway {

    private final ObjectMapper mapper;

    public MockModelGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "deterministic-mock-v1";
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        ObjectNode output = mapper.createObjectNode();
        if (AgentContract.PROMPT_VERSION.equals(request.promptVersion())) {
            String explicitIntent = readContext(request.userPrompt()).path("explicitIntent")
                    .asText("KNOWLEDGE_ONLY");
            output.put("suggestedIntent", explicitIntent);
            output.put("rationale", "The mock preserves the user's explicit intent.");
        } else if (GraphContract.TARGET_PROMPT_VERSION.equals(request.promptVersion())) {
            output.put("route", "NEEDS_CONFIRMATION");
            output.putNull("matchedTopicId");
            output.put("suggestedTopicTitle", "待理解内容");
            output.put("rationale", "The mock cannot make a semantic topic match.");
        } else if (GraphContract.SEMANTIC_PROMPT_VERSION.equals(request.promptVersion())) {
            JsonNode context = readContext(request.userPrompt());
            output.put("topicTitle", context.path("targetTopicTitle").asText("待理解内容"));
            output.put("stageUnderstanding", "新增线索已进入待确认理解，当前不能形成稳定结论。");
            ArrayNode stageEvidenceIds = output.putArray("stageUnderstandingEvidenceIds");
            context.path("evidences").forEach(value ->
                    stageEvidenceIds.add(value.path("evidenceId").asText()));
            ArrayNode changes = output.putArray("changes");
            ObjectNode change = changes.addObject();
            change.put("changeType", "ADD");
            change.put("nodeType", "OPEN_QUESTION");
            change.putNull("targetNodeId");
            change.put("content", "这些线索是否会在后续记录中重复出现？");
            ArrayNode evidenceIds = change.putArray("evidenceIds");
            context.path("evidences").forEach(value ->
                    evidenceIds.add(value.path("evidenceId").asText()));
            output.put("changeSummary", "加入新的待确认线索，并保留不确定性。");
        } else if (GraphContract.ACTION_PROMPT_VERSION.equals(request.promptVersion())) {
            JsonNode context = readContext(request.userPrompt());
            output.put("actionType", "RECORD_BODY");
            output.put("title", "Record one related body change");
            output.put("description",
                    "Record when it occurs and how strong it feels, or report that it did not occur.");
            ArrayNode actionEvidenceIds = output.putArray("evidenceIds");
            context.path("evidences").forEach(value ->
                    actionEvidenceIds.add(value.path("evidenceId").asText()));
            output.put("rationale",
                    "One observation can answer the open question without assuming a diagnosis.");
        } else {
            throw new IllegalArgumentException("unsupported mock prompt version");
        }
        return new ModelResponse(providerName(), modelName(), output,
                null, null, null, null);
    }

    private JsonNode readContext(String userPrompt) {
        try {
            int marker = userPrompt.lastIndexOf('\n');
            String json = marker >= 0 ? userPrompt.substring(marker + 1) : userPrompt;
            return mapper.readTree(json);
        } catch (Exception exception) {
            return mapper.createObjectNode();
        }
    }
}
