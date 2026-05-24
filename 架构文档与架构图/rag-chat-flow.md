# RAG Chat 全流程梳理

## 1. 总览

RAG Chat 的主链路是：

```text
HTTP GET /rag/v3/chat
  -> RAGChatController.chat
  -> RAGChatServiceImpl.streamChat
  -> ChatQueueLimiter.enqueue
  -> StreamChatTraceRunner.run
  -> StreamChatPipeline.execute
      -> loadMemory
      -> rewriteQuery
      -> resolveIntents
      -> handleGuidance?        命中则直接 SSE 返回引导语
      -> handleSystemOnly?      命中则系统闲聊/自我介绍，不检索
      -> retrieve               多通道知识检索
      -> handleEmptyRetrieval?  无结果则直接兜底
      -> streamRagResponse      Prompt 组装 + LLM 流式输出
  -> StreamChatEventHandler
      -> message/finish/done SSE 事件
      -> 引用 noteReferences 组装
```

## 2. HTTP 入口：`RAGChatController`

入口接口为 `GET /rag/v3/chat`，返回 `text/event-stream`。接口参数包括：

- `question`：用户问题，必填。
- `conversationId`：会话 ID，可选；为空时后续服务层生成。
- `deepThinking`：是否开启 thinking，默认 `false`。

关键代码：

```java
@GetMapping(value = "/rag/v3/chat", produces = "text/event-stream;charset=UTF-8")
public SseEmitter chat(@RequestParam String question,
                       @RequestParam(required = false) String conversationId,
                       @RequestParam(required = false, defaultValue = "false") Boolean deepThinking) {
    log.info("[RAGChatController] 开始 RAG 对话, question={}, conversationId={}, deepThinking={}",
            question, conversationId, deepThinking);
    SseEmitter emitter = new SseEmitter(ragDefaultProperties.getSseTimeoutMs());
    ragChatService.streamChat(question, conversationId, deepThinking, emitter);
    return emitter;
}
```

停止接口是 `POST /rag/v3/stop`，通过 `taskId` 取消正在运行的流式任务：

```java
@PostMapping(value = "/rag/v3/stop")
public Result<Void> stop(@RequestParam String taskId) {
    ragChatService.stopTask(taskId);
    return Results.success();
}
```

## 3. 服务入口：生成会话、任务、SSE callback

`RAGChatServiceImpl.streamChat` 做了四件事：

1. 如果前端没有传 `conversationId`，用雪花 ID 生成一个新的会话 ID。
2. 每次请求生成一个新的 `taskId`。
3. 创建 `StreamCallback`，后续 LLM 的内容、thinking、完成、异常都通过它转成 SSE。
4. 进入 `ChatQueueLimiter.enqueue`，再进入 trace 包装，最后调用 `StreamChatPipeline.execute`。

关键代码：

```java
public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
    String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
    String taskId = IdUtil.getSnowflakeNextIdStr();
    StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);
    log.info("[RAGChatService] 收到流式对话请求, conversationId={}, taskId={}, deepThinking={}, question={}",
            actualConversationId, taskId, Boolean.TRUE.equals(deepThinking), question);

    chatQueueLimiter.enqueue(question, actualConversationId, emitter,
            () -> traceRunner.run(question, actualConversationId, taskId, callback, traceAware -> {
                StreamChatContext ctx = StreamChatContext.builder()
                        .question(question)
                        .conversationId(actualConversationId)
                        .taskId(taskId)
                        .deepThinking(Boolean.TRUE.equals(deepThinking))
                        .userId(UserContext.getUserId())
                        .callback(traceAware)
                        .build();
                chatPipeline.execute(ctx);
            }));
}
```

这里有一个很重要的设计点：`RAGChatServiceImpl` 不决定走系统问答还是 RAG 检索，它只构建上下文并交给 pipeline。真正的分支判断都在 `StreamChatPipeline` 内。

## 4. Pipeline 主流程

`StreamChatPipeline.execute` 是 RAG Chat 的主编排函数。代码注释中已经明确了完整链路：

```java
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

    ctx.setRetrievedChunks(retrievalCtx.getAllChunks());

    streamRagResponse(ctx, retrievalCtx);
}
```

这段代码可以理解成一个短路式 pipeline：前面的阶段准备上下文；后面的阶段根据场景短路返回，只有真正需要知识库回答时才进入检索和 RAG Prompt。

## 5. 阶段一：加载并追加会话记忆

`loadMemory` 调用 `ConversationMemoryService.loadAndAppend`：先加载历史，再把当前用户问题追加到会话。

```java
private void loadMemory(StreamChatContext ctx) {
    List<ChatMessage> history = memoryService.loadAndAppend(
            ctx.getConversationId(),
            ctx.getUserId(),
            ChatMessage.user(ctx.getQuestion())
    );
    ctx.setHistory(history);
}
```

接口默认实现说明了这一点：

```java
default List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
    List<ChatMessage> history = load(conversationId, userId);
    append(conversationId, userId, message);
    return history;
}
```

这个阶段的作用：

- 给后续 query rewrite 提供最近上下文。
- 给最终 LLM 回答提供多轮历史。
- 确保用户消息先落入会话历史，assistant 完成后再追加回答。

如果缺少这一层，会导致多轮追问无法利用历史，例如“那这个怎么办？”无法还原指代对象。

## 6. 阶段二：问题改写与多问句拆分

Pipeline 调用：

```java
private void rewriteQuery(StreamChatContext ctx) {
    RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
    ctx.setRewriteResult(rewriteResult);
}
```

当前实现类是 `MultiQuestionRewriteService`。它的逻辑是：

1. 如果关闭 query rewrite：只做术语归一化和规则拆分。
2. 如果开启 query rewrite：先做术语归一化，再调用 LLM，根据 prompt 输出 JSON。
3. 如果 LLM 调用或解析失败：回退到归一化问题。

关键代码：

```java
@Override
@RagTraceNode(name = "query-rewrite-and-split", type = "REWRITE")
public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
    if (!ragConfigProperties.getQueryRewriteEnabled()) {
        String normalized = queryTermMappingService.normalize(userQuestion);
        List<String> subs = ruleBasedSplit(normalized);
        return new RewriteResult(normalized, subs);
    }

    String normalizedQuestion = queryTermMappingService.normalize(userQuestion);

    return callLLMRewriteAndSplit(normalizedQuestion, userQuestion, history);
}
```

LLM 改写请求只保留最近 1-2 轮 User/Assistant 消息，避免历史过长：

```java
if (CollUtil.isNotEmpty(history)) {
    List<ChatMessage> recentHistory = history.stream()
            .filter(msg -> msg.getRole() == ChatMessage.Role.USER
                    || msg.getRole() == ChatMessage.Role.ASSISTANT)
            .skip(Math.max(0, history.size() - 4))
            .toList();
    messages.addAll(recentHistory);
}
```

这个阶段的作用：

- 把口语化问题改写成更适合检索的 query。
- 拆分复合问题，让每个子问题可以独立做意图识别与检索。
- 通过术语映射提升命中率。

如果缺少这一层，检索会更依赖用户原句，长问题、多问句、指代问题的召回效果会下降。

## 7. 阶段三：意图识别

Pipeline 调用：

```java
private void resolveIntents(StreamChatContext ctx) {
    List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
    ctx.setSubIntents(subIntents);
}
```

`IntentResolver.resolve` 会对每个子问题并行分类：

```java
public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
    List<String> subQuestions = CollUtil.isNotEmpty(rewriteResult.subQuestions())
            ? rewriteResult.subQuestions()
            : List.of(rewriteResult.rewrittenQuestion());
    List<CompletableFuture<SubQuestionIntent>> tasks = subQuestions.stream()
            .map(q -> CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return new SubQuestionIntent(q, classifyIntents(q));
                        } catch (Exception e) {
                            log.error("子问题意图分类失败，降级为空意图，question：{}", q, e);
                            return new SubQuestionIntent(q, List.of());
                        }
                    },
                    intentClassifyExecutor
            ))
            .toList();
    List<SubQuestionIntent> subIntents = tasks.stream()
            .map(CompletableFuture::join)
            .toList();
    return capTotalIntents(subIntents);
}
```

意图过滤规则：分数必须达到 `INTENT_MIN_SCORE`，总数受 `MAX_INTENT_COUNT` 限制。

```java
private List<NodeScore> classifyIntents(String question) {
    List<NodeScore> scores = intentClassifier.classifyTargets(question);
    return scores.stream()
            .filter(ns -> ns.getScore() >= INTENT_MIN_SCORE)
            .limit(MAX_INTENT_COUNT)
            .toList();
}
```

这个阶段的作用：

- 判断问题属于系统类、知识库类、MCP 类等意图节点。
- 后续系统问答、歧义引导、检索范围、Prompt 模板都会依赖意图结果。

如果缺少这一层，系统无法区分“你好/你是谁”和真实知识问答，也无法做定向检索。

## 8. 阶段四：歧义引导短路

Pipeline 会先检查是否需要引导用户补充问题：

```java
private boolean handleGuidance(StreamChatContext ctx) {
    GuidanceDecision decision = guidanceService.detectAmbiguity(
            ctx.getRewriteResult().rewrittenQuestion(),
            ctx.getSubIntents()
    );
    if (!decision.isPrompt()) {
        return false;
    }
    StreamCallback callback = ctx.getCallback();
    callback.onContent(decision.getPrompt());
    callback.onComplete();
    return true;
}
```

一旦 `decision.isPrompt()` 为 true，就直接把引导语通过 SSE 返回并完成，不再检索、不再调用 RAG Prompt。

这个阶段的作用：

- 对意图不清、问题过宽或需要澄清的问题先追问。
- 避免拿模糊问题直接检索，导致回答偏题。

如果缺少这一层，系统可能在信息不足时强行回答，增加误导风险。目前是两个意图的差距在0.8以内会进行触发。不过这个不是很重要。

## 9. 阶段五：System-only 分支

如果所有子问题都是 system-only，例如“你好”“你是谁”“你能做什么”，系统不走知识库检索，直接用系统提示词调用 LLM：

```java
private boolean handleSystemOnly(StreamChatContext ctx) {
    List<SubQuestionIntent> subIntents = ctx.getSubIntents();
    boolean allSystemOnly = subIntents.stream()
            .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
    if (!allSystemOnly) {
        return false;
    }
    String customPrompt = subIntents.stream()
            .flatMap(si -> si.nodeScores().stream())
            .map(ns -> ns.getNode().getPromptTemplate())
            .filter(StrUtil::isNotBlank)
            .findFirst()
            .orElse(null);
    StreamCancellationHandle handle = streamSystemResponse(
            ctx.getRewriteResult().rewrittenQuestion(),
            ctx.getHistory(),
            customPrompt,
            ctx.getCallback()
    );
    taskManager.bindHandle(ctx.getTaskId(), handle);
    return true;
}
```

`isSystemOnly` 的判断非常严格：只有一个意图，且该意图节点 kind 是 `SYSTEM`。

```java
public boolean isSystemOnly(List<NodeScore> nodeScores) {
    return nodeScores.size() == 1
            && nodeScores.get(0).getNode() != null
            && nodeScores.get(0).getNode().getKind() == SYSTEM;
}
```

系统回复的消息构造方式：

```java
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

    ChatRequest req = ChatRequest.builder()
            .messages(messages)
            .temperature(0.7D)
            .thinking(false)
            .build();
    return llmService.streamChat(req, callback);
}
```

默认系统提示词路径为 `answer-chat-system.st`。该模板定义了女性健康科普助手的人设、服务范围、闲聊/自我介绍/越界问题的回答策略。

## 10. 阶段六：知识检索

如果没有命中引导或 system-only，就进入检索：

```java
private RetrievalContext retrieve(StreamChatContext ctx) {
    RetrievalContext retrievalCtx = retrievalEngine.retrieve(ctx.getSubIntents(), searchProperties.getDefaultTopK());
    log.info("[StreamChatPipeline] 检索完成, conversationId={}, taskId={}, topK={}, chunkCount={}, hasMcp={}, hasKb={}",
            ctx.getConversationId(), ctx.getTaskId(), searchProperties.getDefaultTopK(),
            retrievalCtx.getAllChunks() != null ? retrievalCtx.getAllChunks().size() : 0,
            retrievalCtx.hasMcp(), retrievalCtx.hasKb());
    return retrievalCtx;
}
```

`RetrievalEngine.retrieve` 对每个子问题并行构建上下文：

```java
List<CompletableFuture<SubQuestionContext>> tasks = subIntents.stream()
        .map(si -> CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return buildSubQuestionContext(
                                si,
                                resolveSubQuestionTopK(si, finalTopK)
                        );
                    } catch (Exception e) {
                        log.error("子问题上下文构建失败，降级为空上下文，question：{}", si.subQuestion(), e);
                        return new SubQuestionContext(si.subQuestion(), "", "", Map.of());
                    }
                },
                ragContextExecutor
        ))
        .toList();
```

每个子问题会拆出 KB 意图。

```java
private SubQuestionContext buildSubQuestionContext(SubQuestionIntent intent, int topK) {
    List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());
    List<NodeScore> mcpIntents = NodeScoreFilters.mcp(intent.nodeScores());

    KbResult kbResult = retrieveAndRerank(intent, kbIntents, topK);

    String mcpContext = CollUtil.isNotEmpty(mcpIntents)
            ? executeMcpAndMerge(intent.subQuestion(), mcpIntents)
            : "";

    return new SubQuestionContext(intent.subQuestion(), kbResult.groupedContext(), mcpContext, kbResult.intentChunks());
}
```

KB 检索调用 `MultiChannelRetrievalEngine.retrieveKnowledgeChannels`，然后按意图节点分组并格式化上下文：

```java
private KbResult retrieveAndRerank(SubQuestionIntent intent, List<NodeScore> kbIntents, int topK) {
    List<SubQuestionIntent> subIntents = List.of(intent);
    List<RetrievedChunk> chunks = multiChannelRetrievalEngine.retrieveKnowledgeChannels(subIntents, topK);

    if (CollUtil.isEmpty(chunks)) {
        return KbResult.empty();
    }

    Map<String, List<RetrievedChunk>> intentChunks = new HashMap<>();

    if (CollUtil.isNotEmpty(kbIntents)) {
        for (NodeScore ns : kbIntents) {
            intentChunks.put(ns.getNode().getId(), chunks);
        }
    } else {
        intentChunks.put(MULTI_CHANNEL_KEY, chunks);
    }

    String groupedContext = contextFormatter.formatKbContext(kbIntents, intentChunks, topK);
    return new KbResult(groupedContext, intentChunks);
}
```

`MultiChannelRetrievalEngine` 的职责是多通道并行检索 + 后置处理链：

```java
public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
    SearchContext context = buildSearchContext(subIntents, topK);

    List<SearchChannelResult> channelResults = executeSearchChannels(context);
    if (CollUtil.isEmpty(channelResults)) {
        return List.of();
    }

    return executePostProcessors(channelResults, context);
}
```

多通道执行时，会筛选启用的通道，并按优先级排序后并行执行：

```java
List<SearchChannel> enabledChannels = searchChannels.stream()
        .filter(channel -> channel.isEnabled(context))
        .sorted(Comparator.comparingInt(SearchChannel::getPriority))
        .toList();

List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
        .map(channel -> CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return channel.search(context);
                    } catch (Exception e) {
                        log.error("检索通道 {} 执行失败", channel.getName(), e);
                        return emptyResult(channel);
                    }
                },
                ragRetrievalExecutor
        ))
        .toList();
```

这个阶段的作用：

- 根据意图和子问题做定向知识检索。
- 支持多个检索通道并行召回。
- 通过后置处理器做去重、rerank 等处理。
- 输出两类数据：给 LLM 的文本上下文，以及给前端 finish 事件用的 `RetrievedChunk` 引用。

如果缺少这一层，后续回答只能靠模型自身知识，无法“基于知识库内容回答”。

## 11. 阶段七：空检索兜底

如果检索结果为空（每个分块的得分都不超过0.3），pipeline 不调用 LLM，直接返回固定提示：

```java
private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
    if (!retrievalCtx.isEmpty()) {
        return false;
    }
    StreamCallback callback = ctx.getCallback();
    callback.onContent("未检索到与问题相关的文档内容。");
    callback.onComplete();
    return true;
}
```

这个设计可以避免在没有证据的情况下仍然构造 RAG Prompt，让 LLM 自行发挥。

## 12. 阶段八：RAG Prompt 组装

检索非空后进入 `streamRagResponse`：

```java
private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
    IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());

    List<RetrievedChunk> chunks = ctx.getRetrievedChunks();
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
```

这里有两个关键动作：

1. 合并所有子问题的意图，供 Prompt 规划使用。
2. 把 retrieved chunks 设置到 callback，后续 finish 事件会从这里生成引用。（用于展示卡片！）

`streamLLMResponse` 构造 `PromptContext`，然后调用 `RAGPromptService.buildStructuredMessages`：

```java
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
```

`RAGPromptService.buildStructuredMessages` 的结构是：

1. system prompt。
2. conversation history。
3. evidence + question 合并成一条 user message。

```java
public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                 List<ChatMessage> history,
                                                 String question,
                                                 List<String> subQuestions) {
    List<ChatMessage> messages = new ArrayList<>();

    String systemPrompt = buildSystemPrompt(context);
    if (StrUtil.isNotBlank(systemPrompt)) {
        messages.add(ChatMessage.system(systemPrompt));
    }

    if (CollUtil.isNotEmpty(history)) {
        messages.addAll(history);
    }

    String evidenceBody = buildEvidenceBody(context);
    String userQuestion = buildUserQuestion(question, subQuestions);
    String userContent = mergeEvidenceAndQuestion(evidenceBody, userQuestion);
    if (StrUtil.isNotBlank(userContent)) {
        messages.add(ChatMessage.user(userContent));
    }

    return messages;
}
```

Prompt 模板选择规则：

```java
private String defaultTemplate(PromptScene scene) {
    return switch (scene) {
        case KB_ONLY -> templateLoader.load(RAG_ENTERPRISE_PROMPT_PATH);
        case MCP_ONLY -> templateLoader.load(MCP_ONLY_PROMPT_PATH);
        case MIXED -> templateLoader.load(MCP_KB_MIXED_PROMPT_PATH);
        case EMPTY -> "";
    };
}
```

证据正文会把 MCP 和 KB 上下文分别渲染成 section：

```java
private String buildEvidenceBody(PromptContext context) {
    StringBuilder sb = new StringBuilder();
    if (StrUtil.isNotBlank(context.getMcpContext())) {
        sb.append(renderSection("mcp-evidence", Map.of("body", context.getMcpContext().trim())));
    }
    if (StrUtil.isNotBlank(context.getKbContext())) {
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append(renderSection("kb-evidence", Map.of("body", context.getKbContext().trim())));
    }
    return sb.toString().trim();
}
```

这个阶段的作用：

- 把检索证据转成模型可读上下文。
- 根据 KB / MCP / Mixed 场景选择不同系统模板。
- 保留历史对话，使回答符合上下文。
- 对多问句，用编号形式呈现子问题。

如果缺少这一层，检索结果无法稳定地进入 LLM，模型也无法知道回答约束和证据格式。

## 13. 阶段九：LLM 调用

业务层只依赖 `LLMService` 接口，不直接关心底层模型供应商：

```java
public interface LLMService {
    String chat(ChatRequest request);

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);
}
```

其中：

- query rewrite 使用同步 `chat(req)`，因为需要一次性拿 JSON。
- system-only 和 RAG 回答使用 `streamChat(req, callback)`，因为前端需要 SSE 增量输出。
- 返回的 `StreamCancellationHandle` 会绑定到 `StreamTaskManager`，供 `/rag/v3/stop` 取消。

## 14. 阶段十：SSE 输出、完成事件与持久化

`StreamCallbackFactory` 创建 `StreamChatEventHandler`：

```java
public StreamCallback createChatEventHandler(SseEmitter emitter,
                                             String conversationId,
                                             String taskId) {
    StreamChatHandlerParams params = StreamChatHandlerParams.builder()
            .emitter(emitter)
            .conversationId(conversationId)
            .taskId(taskId)
            .modelProperties(modelProperties)
            .memoryService(memoryService)
            .conversationGroupService(conversationGroupService)
            .taskManager(taskManager)
            .searchChannelProperties(searchChannelProperties)
            .build();

    return new StreamChatEventHandler(params);
}
```

`StreamChatEventHandler` 初始化时会立刻发送 `meta` 事件，并注册任务：

```java
private void initialize() {
    sender.sendEvent(SSEEventType.META.value(), new MetaPayload(conversationId, taskId));
    taskManager.register(taskId, sender, this::buildCompletionPayloadOnCancel);
}
```

LLM 普通内容通过 `onContent` 进入：

```java
@Override
public void onContent(String chunk) {
    if (taskManager.isCancelled(taskId)) {
        return;
    }
    if (StrUtil.isBlank(chunk)) {
        return;
    }
    if (thinkingStartMs > 0 && thinkingDurationSeconds == 0) {
        thinkingDurationSeconds = Math.max(1, Math.round((System.currentTimeMillis() - thinkingStartMs) / 1000.0f));
    }
    answer.append(chunk);
    sendChunked(TYPE_RESPONSE, chunk);
}
```

thinking 内容通过 `onThinking` 进入：

```java
@Override
public void onThinking(String chunk) {
    if (taskManager.isCancelled(taskId)) {
        return;
    }
    if (StrUtil.isBlank(chunk)) {
        return;
    }
    if (thinkingStartMs == 0) {
        thinkingStartMs = System.currentTimeMillis();
    }
    thinking.append(chunk);
    sendChunked(TYPE_THINK, chunk);
}
```

`sendChunked` 会按配置的 `messageChunkSize` 拆分成 SSE `message` 事件：

```java
private void sendChunked(String type, String content) {
    int length = content.length();
    int idx = 0;
    int count = 0;
    StringBuilder buffer = new StringBuilder();
    while (idx < length) {
        int codePoint = content.codePointAt(idx);
        buffer.appendCodePoint(codePoint);
        idx += Character.charCount(codePoint);
        count++;
        if (count >= messageChunkSize) {
            sender.sendEvent(SSEEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
            buffer.setLength(0);
            count = 0;
        }
    }
    if (!buffer.isEmpty()) {
        sender.sendEvent(SSEEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
    }
}
```

完成时会：

1. 把 assistant 回答追加到会话记忆。
2. 生成标题信息。
3. 根据 retrieved chunks 生成笔记引用。
4. 发送 `finish` 事件。
5. 发送 `done` 事件。
6. 注销任务并 complete emitter。

```java
@Override
public void onComplete() {
    if (taskManager.isCancelled(taskId)) {
        return;
    }
    String messageId = null;
    try {
        String thinkingContent = thinking.isEmpty() ? null : thinking.toString();
        ChatMessage message = ChatMessage.assistant(answer.toString(), thinkingContent, resolveThinkingDuration());
        messageId = memoryService.append(conversationId, userId, message);
    } catch (Exception e) {
        log.error("对话完成时持久化消息失败，conversationId：{}", conversationId, e);
    }
    String title = resolveTitleForEvent();
    String messageIdText = StrUtil.isBlank(messageId) ? null : messageId;
    List<CompletionPayload.NoteReference> references = buildNoteReferences();
    sender.sendEvent(SSEEventType.FINISH.value(), new CompletionPayload(messageIdText, title, references));
    sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
    taskManager.unregister(taskId);
    sender.complete();
}
```

## 15. 前端可观察到的 SSE 事件

从当前代码可以确认至少有这些事件：

| 事件 | 发送位置 | 说明 |
|---|---|---|
| `meta` | `StreamChatEventHandler.initialize` | 返回 `conversationId` 和 `taskId` |
| `message` | `sendChunked` | 流式内容片段，`type=response` 或 `type=think` |
| `finish` | `onComplete` 或取消时 payload | 返回 `messageId`、标题、引用 |
| `done` | `onComplete` | 固定 `[DONE]`，表示 SSE 正常结束 |

## 16. 笔记引用生成逻辑

RAG 分支会在调用 LLM 前执行：

```java
ctx.getCallback().setRetrievedChunks(chunks);
```

完成时 `buildNoteReferences` 从 retrieved chunks 里筛选引用：

```java
List<CompletionPayload.NoteReference> references = retrievedChunks.stream()
        .filter(chunk -> {
            boolean hasMetadata = chunk.getMetadata() != null;
            boolean scoreAboveThreshold = chunk.getScore() >= minScoreThreshold;
            return hasMetadata && scoreAboveThreshold;
        })
        .map(chunk -> {
            Long noteId = extractNoteId(chunk.getMetadata());
            if (noteId == null) {
                return null;
            }
            String title = extractTitle(chunk.getMetadata(), noteId);
            String snippet = buildSnippet(chunk.getText());
            return new CompletionPayload.NoteReference(noteId, title, snippet, chunk.getScore());
        })
        .filter(ref -> ref != null)
        .collect(java.util.stream.Collectors.toMap(
                CompletionPayload.NoteReference::noteId,
                ref -> ref,
                (ref1, ref2) -> ref1.score() > ref2.score() ? ref1 : ref2
        ))
        .values()
        .stream()
        .sorted((r1, r2) -> Double.compare(r2.score(), r1.score()))
        .limit(maxCount)
        .toList();
```

筛选条件：

- chunk 必须有 metadata。
- chunk score 必须大于等于 `searchChannelProperties.noteReference.minScoreThreshold`。（目前设置的是0.5）
- metadata 中必须能提取 `noteId`。
- 同一个 noteId 多个 chunk 时保留分数更高的。
- 最终按分数降序，限制最大数量。

## 17. Trace 链路

入口服务用 `StreamChatTraceRunner.run` 包住 pipeline：

```java
chatQueueLimiter.enqueue(question, actualConversationId, emitter,
        () -> traceRunner.run(question, actualConversationId, taskId, callback, traceAware -> {
            StreamChatContext ctx = StreamChatContext.builder()
                    .question(question)
                    .conversationId(actualConversationId)
                    .taskId(taskId)
                    .deepThinking(Boolean.TRUE.equals(deepThinking))
                    .userId(UserContext.getUserId())
                    .callback(traceAware)
                    .build();
            chatPipeline.execute(ctx);
        }));
```

同时多个核心阶段标注了 `@RagTraceNode`，例如：

- `MultiQuestionRewriteService.rewriteWithSplit`：`query-rewrite-and-split`
- `IntentResolver.resolve`：`intent-resolve`
- `RetrievalEngine.retrieve`：`retrieval-engine`
- `MultiChannelRetrievalEngine.retrieveKnowledgeChannels`：`multi-channel-retrieval`

记录 RAG 运行链路，用于后续 trace 查询和排障。

## 18. 三条典型执行路径

### 18.1 闲聊 / 自我介绍

```text
/rag/v3/chat
  -> loadMemory
  -> rewriteQuery
  -> resolveIntents
  -> handleGuidance false
  -> handleSystemOnly true
  -> streamSystemResponse
  -> LLM stream
  -> SSE message/finish/done
```

特点：不检索知识库，使用 `answer-chat-system.st` 或意图节点自定义 prompt。

### 18.2 正常知识库问答

```text
/rag/v3/chat
  -> loadMemory
  -> rewriteQuery
  -> resolveIntents
  -> handleGuidance false
  -> handleSystemOnly false
  -> RetrievalEngine.retrieve
  -> MultiChannelRetrievalEngine.retrieveKnowledgeChannels
  -> RAGPromptService.buildStructuredMessages
  -> LLM stream
  -> SSE message/finish/done + noteReferences
```

特点：检索结果进入 Prompt，finish 事件可能带笔记引用。

### 18.3 检索为空

```text
/rag/v3/chat
  -> loadMemory
  -> rewriteQuery
  -> resolveIntents
  -> handleGuidance false
  -> handleSystemOnly false
  -> retrieve empty
  -> callback.onContent("未检索到与问题相关的文档内容。")
  -> callback.onComplete
```

特点：不调用 LLM，直接返回兜底文案。

## 19. 总结

当前 RAG Chat 的核心是 `StreamChatPipeline`：它先用会话历史和 LLM/规则做查询改写，再做意图识别；如果是澄清或系统闲聊就短路返回，否则进入多通道知识检索，把检索上下文通过 `RAGPromptService` 组装进 Prompt，最后通过统一 `LLMService.streamChat` 流式生成，并由 `StreamChatEventHandler` 转成 SSE、落库和引用信息。
