/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        return llmService.chat(buildRequest(messages, temperature, topP, maxTokens));
    }

    @Override
    public String chatWithReportModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
        return llmService.chat(buildRequest(messages, temperature, topP, maxTokens));
    }

    @Override
    public String summarizeConversationMemory(List<ChatMessage> messages, Integer maxTokens) {
        return llmService.chat(buildRequest(messages, 0.2D, 0.3D, maxTokens));
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
