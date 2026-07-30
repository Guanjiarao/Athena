

package com.nageoffer.ai.ragent.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
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
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 系统响应 / 检索 -> Prompt 组装 -> 流式输出
 * <p>
 * 流水线模式：通过私有方法 + boolean 返回值（handleXxx 返回 true 表示已处理并短路）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private final SearchChannelProperties searchProperties;
    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;

    /**
     * 执行流式对话管道。
     *
     * 调用链说明：
     * 1. RAGChatServiceImpl.streamChat 负责生成 conversationId/taskId、创建 SSE callback，并进入队列与 trace 包装。
     * 2. StreamChatPipeline.execute 负责实际业务编排：加载记忆 -> 问题改写 -> 意图识别 -> 分支处理。
     * 3. 如果命中歧义引导，直接返回引导语，不调用 LLM 检索回答。
     * 4. 如果所有意图都是 system-only（如“你好”“你是谁”“你能做什么”），走 answer-chat-system.st。
     * 5. 否则先检索知识，再走 RAG prompt（如 answer-chat-kb.st / MCP 相关 prompt）。
     */
    public void execute(StreamChatContext ctx) {
        log.info("[StreamChatPipeline] 开始执行对话流水线, conversationId={}, taskId={}, question={}",
                ctx.getConversationId(), ctx.getTaskId(), ctx.getQuestion());

        loadMemory(ctx);
        rewriteQuery(ctx);
        resolveIntents(ctx);

        if (handleGuidance(ctx)) {
            return;
        }
        if (handleSystemOnly(ctx)) {
            return;
        }

        RetrievalContext retrievalCtx = retrieve(ctx);
        if (handleEmptyRetrieval(ctx, retrievalCtx)) {
            return;
        }

        // 保存检索到的 chunks 到 context，用于 finish 事件返回
        ctx.setRetrievedChunks(retrievalCtx.getAllChunks());

        streamRagResponse(ctx, retrievalCtx);
    }

    // ==================== 流水线阶段 ====================

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
        log.info("[StreamChatPipeline] 记忆加载完成, conversationId={}, taskId={}, historySize={}",
                ctx.getConversationId(), ctx.getTaskId(), history != null ? history.size() : 0);
    }

    private void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        ctx.setRewriteResult(rewriteResult);
        log.info("[StreamChatPipeline] 问题改写完成, conversationId={}, taskId={}, rewrittenQuestion={}, subQuestionCount={}",
                ctx.getConversationId(), ctx.getTaskId(), rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions() != null ? rewriteResult.subQuestions().size() : 0);
    }

    private void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
        log.info("[StreamChatPipeline] 意图识别完成, conversationId={}, taskId={}, subIntentCount={}",
                ctx.getConversationId(), ctx.getTaskId(), subIntents != null ? subIntents.size() : 0);
    }

    private boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getSubIntents()
        );
        if (!decision.isPrompt()) {
            log.info("[StreamChatPipeline] 未命中歧义引导分支, conversationId={}, taskId={}",
                    ctx.getConversationId(), ctx.getTaskId());
            return false;
        }
        log.info("[StreamChatPipeline] 命中歧义引导分支, conversationId={}, taskId={}, prompt={}",
                ctx.getConversationId(), ctx.getTaskId(), decision.getPrompt());
        StreamCallback callback = ctx.getCallback();
        callback.onContent(decision.getPrompt());
        callback.onComplete();
        return true;
    }

    private boolean handleSystemOnly(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = ctx.getSubIntents();
        boolean allSystemOnly = subIntents.stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (!allSystemOnly) {
            log.info("[StreamChatPipeline] 未命中 system-only 分支, 将进入检索流程, conversationId={}, taskId={}",
                    ctx.getConversationId(), ctx.getTaskId());
            return false;
        }
        String customPrompt = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .map(ns -> ns.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        log.info("[StreamChatPipeline] 命中 system-only 分支, 使用{}系统提示词, conversationId={}, taskId={}, promptPath={}",
                StrUtil.isNotBlank(customPrompt) ? "意图节点自定义" : "默认",
                ctx.getConversationId(), ctx.getTaskId(),
                StrUtil.isNotBlank(customPrompt) ? "intentNode.promptTemplate" : CHAT_SYSTEM_PROMPT_PATH);
        StreamCancellationHandle handle = streamSystemResponse(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getHistory(),
                customPrompt,
                ctx.getCallback()
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
        return true;
    }

    private RetrievalContext retrieve(StreamChatContext ctx) {
        RetrievalContext retrievalCtx = retrievalEngine.retrieve(ctx.getSubIntents(), searchProperties.getDefaultTopK());
        log.info("[StreamChatPipeline] 检索完成, conversationId={}, taskId={}, topK={}, chunkCount={}, hasMcp={}, hasKb={}",
                ctx.getConversationId(), ctx.getTaskId(), searchProperties.getDefaultTopK(),
                retrievalCtx.getAllChunks() != null ? retrievalCtx.getAllChunks().size() : 0,
                retrievalCtx.hasMcp(), retrievalCtx.hasKb());
        return retrievalCtx;
    }

    private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        if (!retrievalCtx.isEmpty()) {
            return false;
        }
        log.info("[StreamChatPipeline] 检索结果为空, 直接返回兜底提示, conversationId={}, taskId={}",
                ctx.getConversationId(), ctx.getTaskId());
        StreamCallback callback = ctx.getCallback();
        callback.onContent("未检索到与问题相关的文档内容。");
        callback.onComplete();
        return true;
    }

    private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        // 聚合所有意图用于 prompt 规划
        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());
        log.info("[StreamChatPipeline] 命中 RAG 回复分支, conversationId={}, taskId={}, mcpIntentCount={}, kbIntentCount={}",
                ctx.getConversationId(), ctx.getTaskId(),
                mergedGroup.mcpIntents() != null ? mergedGroup.mcpIntents().size() : 0,
                mergedGroup.kbIntents() != null ? mergedGroup.kbIntents().size() : 0);

        // 设置检索到的 chunks 到 callback（用于 finish 事件返回）
        List<RetrievedChunk> chunks = ctx.getRetrievedChunks();
        log.info("[StreamChatPipeline] 设置 {} 个 chunks 到 callback, conversationId={}, taskId={}",
                chunks != null ? chunks.size() : 0, ctx.getConversationId(), ctx.getTaskId());
        ctx.getCallback().setRetrievedChunks(chunks);

        StreamCancellationHandle handle = streamLLMResponse(
                ctx.getRewriteResult(),
                retrievalCtx,
                mergedGroup,
                ctx.getHistory(),
                ctx.isDeepThinking(),
                ctx.getCallback()
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

    // ==================== LLM 响应 ====================

    private StreamCancellationHandle streamSystemResponse(String question, List<ChatMessage> history,
                                                          String customPrompt, StreamCallback callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));

        log.info("[StreamChatPipeline] 调用 LLM 生成 system-only 回复, promptSource={}, messageCount={}, question={}",
                StrUtil.isNotBlank(customPrompt) ? "intentNode.promptTemplate" : CHAT_SYSTEM_PROMPT_PATH,
                messages.size(), question);
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
                rewriteResult.subQuestions()  // 传入子问题列表
        );
        log.info("[StreamChatPipeline] 调用 LLM 生成 RAG 回复, hasMcp={}, hasKb={}, deepThinking={}, messageCount={}, rewrittenQuestion={}",
                ctx.hasMcp(), ctx.hasKb(), deepThinking, messages.size(), rewriteResult.rewrittenQuestion());
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)  // MCP 场景稍微放宽温度
                .topP(ctx.hasMcp() ? 0.8D : 1D)
                .build();

        return llmService.streamChat(chatRequest, callback);
    }
}
