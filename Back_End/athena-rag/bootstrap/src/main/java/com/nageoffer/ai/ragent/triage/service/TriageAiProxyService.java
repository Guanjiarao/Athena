

package com.nageoffer.ai.ragent.triage.service;

import com.nageoffer.ai.ragent.triage.controller.request.ClientChatCompletionRequest;
import com.nageoffer.ai.ragent.triage.controller.request.VisionAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.ClientChatCompletionResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.VisionAnalyzeResponse;

/**
 * 面向前端的 AI 代理服务。
 *
 * <p>用于彻底下线前端 API Key，把文本和视觉类模型调用统一收口到后端。</p>
 */
public interface TriageAiProxyService {

    /**
     * 代理文本补全能力。
     */
    ClientChatCompletionResponse complete(ClientChatCompletionRequest request);

    /**
     * 代理视觉分析能力。
     */
    VisionAnalyzeResponse analyzeVision(VisionAnalyzeRequest request);
}
