

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
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
    public String chatWithTextModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        return llmService.chat(buildRequest(triageAiProperties.getTextModel(), messages, temperature, topP, maxTokens));
    }

    @Override
    public String chatWithReportModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        return llmService.chat(buildRequest(triageAiProperties.getReportModel(), messages, temperature, topP, maxTokens));
    }

    @Override
    public String summarizeConversationMemory(List<ChatMessage> messages, Integer maxTokens) {
        return llmService.chat(buildRequest(triageAiProperties.getTextModel(), messages, 0.2D, 0.3D, maxTokens));
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

    private ChatRequest buildRequest(String modelId, List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        return ChatRequest.builder()
                // .modelId(StrUtil.isBlank(modelId) ? null : modelId)  // modelId 字段已从 ChatRequest 中移除
                .messages(CollUtil.isEmpty(messages) ? List.of() : messages)
                .temperature(temperature)
                .topP(topP)
                .thinking(false)
                .maxTokens(maxTokens)
                .build();
    }
}
