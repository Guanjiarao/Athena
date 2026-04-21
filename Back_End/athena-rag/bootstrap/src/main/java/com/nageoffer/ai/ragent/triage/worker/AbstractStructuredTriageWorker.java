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

package com.nageoffer.ai.ragent.triage.worker;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 三个 Worker 共用的结构化输出基类。
 *
 * <p>核心职责：
 * 1. 统一通过已有 {@link LLMService} 基座调用模型，避免直接接触底层供应商；
 * 2. 统一做 Markdown 代码块清洗与 JSON 主体提取；
 * 3. 统一封装 try-catch，保证结构化输出失败时不会把异常直接泄漏到上层编排器。</p>
 */
@Slf4j
public abstract class AbstractStructuredTriageWorker {

    private final LLMService llmService;

    protected final ObjectMapper objectMapper;

    protected AbstractStructuredTriageWorker(LLMService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /**
     * 统一调用模型。
     *
     * <p>这里固定使用低温度、禁用思维链暴露的同步调用，
     * 以换取更稳定的结构化 JSON 输出。</p>
     */
    protected String invokeLlm(String systemPrompt, String userPrompt, double temperature, double topP) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .temperature(temperature)
                .topP(topP)
                .thinking(false)
                .maxTokens(800)
                .build();
        return llmService.chat(request);
    }

    /**
     * 安全解析 JSON 对象。
     */
    protected <T> T readObjectSafely(String rawResponse, Class<T> clazz, T fallbackValue, String scene) {
        if (StrUtil.isBlank(rawResponse)) {
            return fallbackValue;
        }
        try {
            return objectMapper.readValue(extractJsonPayload(rawResponse), clazz);
        } catch (Exception ex) {
            log.warn("{} JSON 对象解析失败，raw={}", scene, abbreviate(rawResponse), ex);
            return fallbackValue;
        }
    }

    /**
     * 安全解析复杂泛型，例如 List<String> 或 List<Symptom>。
     */
    protected <T> T readTypeSafely(String rawResponse,
                                   TypeReference<T> typeReference,
                                   T fallbackValue,
                                   String scene) {
        if (StrUtil.isBlank(rawResponse)) {
            return fallbackValue;
        }
        try {
            return objectMapper.readValue(extractJsonPayload(rawResponse), typeReference);
        } catch (Exception ex) {
            log.warn("{} JSON 泛型解析失败，raw={}", scene, abbreviate(rawResponse), ex);
            return fallbackValue;
        }
    }

    /**
     * 从 LLM 响应中尽量抽取出真正的 JSON 主体。
     *
     * <p>很多模型即使被要求“只返回 JSON”，仍然会包一层 ```json 或补一句解释。
     * 这里统一做清洗，尽量把输入收敛为一个可反序列化的 JSON 片段。</p>
     */
    protected String extractJsonPayload(String rawResponse) {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(rawResponse);
        if (StrUtil.isBlank(cleaned)) {
            return cleaned;
        }
        String trimmed = cleaned.trim();
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        if (start < 0) {
            return trimmed;
        }
        int objectEnd = trimmed.lastIndexOf('}');
        int arrayEnd = trimmed.lastIndexOf(']');
        int end = Math.max(objectEnd, arrayEnd);
        if (end < 0 || end <= start) {
            return trimmed.substring(start);
        }
        return trimmed.substring(start, end + 1);
    }

    /**
     * 安全序列化对象，主要用于把上下文片段嵌入 Prompt。
     */
    protected String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("对象序列化失败，value={}", value, ex);
            return String.valueOf(value);
        }
    }

    /**
     * 对字符串列表做去重、去空白、保序处理。
     */
    protected List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            normalized.add(value.trim());
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 避免日志把整段模型返回全文打爆。
     */
    protected String abbreviate(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
