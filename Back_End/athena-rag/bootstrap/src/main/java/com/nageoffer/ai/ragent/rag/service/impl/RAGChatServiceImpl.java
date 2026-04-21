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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.rag.aop.ChatRateLimit;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.guidance.IntentGuidanceService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptContext;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.prompt.RAGPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.ChatReferencePayload;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import com.nageoffer.ai.ragent.rag.service.handler.StreamCallbackFactory;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;
import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.DEFAULT_TOP_K;

/**
 * RAG 对话服务默认实现
 * <p>
 * 核心流程：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 检索(MCP+KB) -> Prompt 组装 -> 流式输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ConversationMemoryService memoryService;
    private final StreamTaskManager taskManager;
    private final IntentGuidanceService guidanceService;
    private final StreamCallbackFactory callbackFactory;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;

    @Override
    @ChatRateLimit
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String taskId = StrUtil.isBlank(RagTraceContext.getTaskId())
                ? IdUtil.getSnowflakeNextIdStr()
                : RagTraceContext.getTaskId();
        List<ChatReferencePayload> references = new ArrayList<>();
        StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId, references);
        streamChat(question, actualConversationId, deepThinking, taskId, callback, references);
    }

    @Override
    @ChatRateLimit
    public ChatStreamSession streamChat(String question, String conversationId, Boolean deepThinking, StreamCallback callback) {
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String taskId = StrUtil.isBlank(RagTraceContext.getTaskId())
                ? IdUtil.getSnowflakeNextIdStr()
                : RagTraceContext.getTaskId();
        List<ChatReferencePayload> references = new ArrayList<>();
        streamChat(question, actualConversationId, deepThinking, taskId, callback, references);
        return new ChatStreamSession(actualConversationId, taskId, List.copyOf(references));
    }

    private void streamChat(String question,
                            String actualConversationId,
                            Boolean deepThinking,
                            String taskId,
                            StreamCallback callback,
                            List<ChatReferencePayload> references) {
        log.info("开始流式对话，会话ID：{}，任务ID：{}", actualConversationId, taskId);
        boolean thinkingEnabled = Boolean.TRUE.equals(deepThinking);

        String userId = UserContext.getUserId();
        List<ChatMessage> history = memoryService.loadAndAppend(actualConversationId, userId, ChatMessage.user(question));

        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, history);
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);

        GuidanceDecision guidanceDecision = guidanceService.detectAmbiguity(rewriteResult.rewrittenQuestion(), subIntents);
        if (guidanceDecision.isPrompt()) {
            callback.onContent(guidanceDecision.getPrompt());
            callback.onComplete();
            return;
        }

        boolean allSystemOnly = subIntents.stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (allSystemOnly) {
            String customPrompt = subIntents.stream()
                    .flatMap(si -> si.nodeScores().stream())
                    .map(ns -> ns.getNode().getPromptTemplate())
                    .filter(StrUtil::isNotBlank)
                    .findFirst()
                    .orElse(null);
            StreamCancellationHandle handle = streamSystemResponse(rewriteResult.rewrittenQuestion(), history, customPrompt, callback);
            taskManager.bindHandle(taskId, handle);
            return;
        }

        RetrievalContext ctx = retrievalEngine.retrieve(subIntents, DEFAULT_TOP_K);
        if (ctx.isEmpty()) {

            callback.onContent(buildEmptyRetrievalReply(rewriteResult.rewrittenQuestion(), question));
            callback.onComplete();
            return;
        }
        references.addAll(buildReferences(ctx));

        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(subIntents);

        StreamCancellationHandle handle = streamLLMResponse(
                rewriteResult,
                ctx,
                mergedGroup,
                history,
                thinkingEnabled,
                callback
        );
        taskManager.bindHandle(taskId, handle);
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }

    private List<ChatReferencePayload> buildReferences(RetrievalContext ctx) {
        if (ctx == null || ctx.getIntentChunks() == null || ctx.getIntentChunks().isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Long, ChatReferencePayload> references = new LinkedHashMap<>();
        for (List<RetrievedChunk> chunks : ctx.getIntentChunks().values()) {
            if (CollUtil.isEmpty(chunks)) {
                continue;
            }
            for (RetrievedChunk chunk : chunks) {
                Long noteId = extractNoteId(chunk == null ? null : chunk.getMetadata());
                if (noteId == null || references.containsKey(noteId)) {
                    continue;
                }
                references.put(noteId, ChatReferencePayload.builder()
                        .noteId(noteId)
                        .title(extractTitle(chunk.getMetadata(), noteId))
                        .snippet(buildSnippet(chunk.getText()))
                        .score(chunk.getScore())
                        .build());
            }
        }
        return new ArrayList<>(references.values());
    }

    private Long extractNoteId(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object noteId = metadata.get("noteId");
        if (noteId instanceof Number number) {
            return number.longValue();
        }
        if (noteId instanceof String text && text.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(text);
        }
        return null;
    }

    private String extractTitle(Map<String, Object> metadata, Long noteId) {
        if (metadata != null) {
            Object title = metadata.get("title");
            if (title != null && StrUtil.isNotBlank(String.valueOf(title))) {
                return String.valueOf(title);
            }
        }
        return "Athena 笔记 #" + noteId;
    }

    private String buildSnippet(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }
    private String buildEmptyRetrievalReply(String rewrittenQuestion, String originalQuestion) {
        String normalized = StrUtil.blankToDefault(rewrittenQuestion, originalQuestion);
        if (isMedicationDoseQuestion(normalized)) {
            return "根据当前可用资料，还不能判断这个药一次应该吃几片。不同药物、规格和适应症的用法差异很大，不能直接替你确定剂量。建议先确认药名、规格和说明书，必要时咨询医生或药师。";
        }
        if (isHighRiskQuestion(normalized)) {
            return "根据当前可用资料，还不能判断这是不是正常情况。但这类情况不建议仅在家观察，建议尽快就医，建议尽快到医院进一步评估；如果伴有腹痛明显、头晕、出血增多或其他明显不适，请尽快就医，必要时急诊处理。";
        }
        return "根据当前可用资料，暂时还不能直接判断这个问题。现有内容里没有足够信息支持更具体的结论。如果你愿意，可以补充更具体的情况，我再帮你一起梳理。";
    }
    private boolean isMedicationDoseQuestion(String question) {
        return StrUtil.containsAny(question,
                "这个药", "怎么吃", "一次吃几片", "吃几片", "吃多少", "剂量", "用量", "说明书", "药名");
    }
    private boolean isHighRiskQuestion(String question) {
        return StrUtil.containsAny(question,
                "怀孕", "孕早期", "停经", "出血", "流血", "腹痛", "肚子疼", "头晕", "乳房", "硬块", "肿块", "溢液", "异常出血");
    }
    private StreamCancellationHandle streamSystemResponse(String question, List<ChatMessage> history,
                                                          String customPrompt, StreamCallback callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history.subList(0, history.size() - 1));
        }
        messages.add(ChatMessage.user(question));

        ChatRequest req = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        return llmService.streamChat(req, callback);
    }

    private StreamCancellationHandle streamLLMResponse(RewriteResult rewriteResult, RetrievalContext ctx,
                                                       IntentGroup intentGroup, List<ChatMessage> history,
                                                       boolean deepThinking, StreamCallback callback) {
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult.rewrittenQuestion())
                .mcpContext(ctx.getMcpContext())
                .kbContext(ctx.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(ctx.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                history,
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions()
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)
                .topP(ctx.hasMcp() ? 0.8D : 1D)
                .build();

        return llmService.streamChat(chatRequest, callback);
    }
}
