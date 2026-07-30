

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.config.TriageAiProperties;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * triage 场景模型门面实现。
 */
@Service
@RequiredArgsConstructor
public class TriageModelGatewayImpl implements TriageModelGateway {

    private final LLMService llmService;
    private final TriageAiProperties triageAiProperties;

    @Override
    @RagTraceNode(name = "TriageTextModel", type = "TRIAGE_LLM_TEXT")
    public String chatWithTextModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        String modelId = triageAiProperties.getTextModel();
        return llmService.chat(buildRequest(messages, temperature, topP, maxTokens), modelId);
    }

    @Override
    @RagTraceNode(name = "TriageReportModel", type = "TRIAGE_LLM_RPT")
    public String chatWithReportModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        String modelId = triageAiProperties.getReportModel();
        return llmService.chat(buildRequest(messages, temperature, topP, maxTokens), modelId);
    }

    @Override
    @RagTraceNode(name = "TriageMemorySummaryModel", type = "TRIAGE_LLM_MEM")
    public String summarizeConversationMemory(List<ChatMessage> messages, Integer maxTokens) {
        String modelId = triageAiProperties.getTextModel();
        return llmService.chat(buildRequest(messages, 0.2D, 0.3D, maxTokens), modelId);
    }

    @Override
    public String resolveVisionModel(String requestModel) {
        if (StrUtil.isNotBlank(requestModel)) {
            return requestModel;
        }
        if (StrUtil.isNotBlank(triageAiProperties.getVisionModel())) {
            return triageAiProperties.getVisionModel();
        }
        return "qwen-vl-max";
    }

    private ChatRequest buildRequest(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        return ChatRequest.builder()
                .messages(CollUtil.isEmpty(messages) ? List.of() : messages)
                .temperature(temperature)
                .topP(topP)
                .thinking(false)
                .maxTokens(maxTokens)
                .build();
    }
}
