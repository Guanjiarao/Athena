

package com.nageoffer.ai.ragent.triage.service;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;

import java.util.List;

/**
 * triage 场景专属模型门面。
 */
public interface TriageModelGateway {

    /**
     * triage 文本类调用。
     */
    String chatWithTextModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens);

    /**
     * triage 报告生成调用。
     */
    String chatWithReportModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens);

    /**
     * triage 对话摘要调用。
     */
    String summarizeConversationMemory(List<ChatMessage> messages, Integer maxTokens);

    /**
     * triage 视觉模型名称。
     */
    String resolveVisionModel(String requestModel);
}
