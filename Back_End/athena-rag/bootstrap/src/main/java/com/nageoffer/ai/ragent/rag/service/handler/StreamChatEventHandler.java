

package com.nageoffer.ai.ragent.rag.service.handler;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dto.CompletionPayload;
import com.nageoffer.ai.ragent.rag.dto.MessageDelta;
import com.nageoffer.ai.ragent.rag.dto.MetaPayload;
import com.nageoffer.ai.ragent.rag.enums.SSEEventType;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.web.SseEmitterSender;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import lombok.extern.slf4j.Slf4j;
import com.nageoffer.ai.ragent.rag.service.ConversationGroupService;

import java.util.List;
import java.util.Optional;

@Slf4j
public class StreamChatEventHandler implements StreamCallback {

    private static final String TYPE_THINK = "think";
    private static final String TYPE_RESPONSE = "response";

    private final int messageChunkSize;
    private final SseEmitterSender sender;
    private final String conversationId;
    private final ConversationMemoryService memoryService;
    private final ConversationGroupService conversationGroupService;
    private final String taskId;
    private final String userId;
    private final StreamTaskManager taskManager;
    private final boolean sendTitleOnComplete;
    private final SearchChannelProperties searchChannelProperties;
    private final StringBuilder answer = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private long thinkingStartMs;
    private int thinkingDurationSeconds;
    private List<RetrievedChunk> retrievedChunks;

    /**
     * 使用参数对象构造（推荐）
     *
     * @param params 构建参数
     */
    public StreamChatEventHandler(StreamChatHandlerParams params) {
        this.sender = new SseEmitterSender(params.getEmitter());
        this.conversationId = params.getConversationId();
        this.taskId = params.getTaskId();
        this.memoryService = params.getMemoryService();
        this.conversationGroupService = params.getConversationGroupService();
        this.taskManager = params.getTaskManager();
        this.userId = UserContext.getUserId();
        this.searchChannelProperties = params.getSearchChannelProperties();

        // 计算配置
        this.messageChunkSize = resolveMessageChunkSize(params.getModelProperties());
        this.sendTitleOnComplete = shouldSendTitle();

        // 初始化（发送初始事件、注册任务）
        initialize();
    }

    /**
     * 初始化：发送元数据事件并注册任务
     */
    private void initialize() {
        sender.sendEvent(SSEEventType.META.value(), new MetaPayload(conversationId, taskId));
        taskManager.register(taskId, sender, this::buildCompletionPayloadOnCancel);
    }

    /**
     * 解析消息块大小
     */
    private int resolveMessageChunkSize(AIModelProperties modelProperties) {
        return Math.max(1, Optional.ofNullable(modelProperties.getStream())
                .map(AIModelProperties.Stream::getMessageChunkSize)
                .orElse(5));
    }

    /**
     * 判断是否需要发送标题
     */
    private boolean shouldSendTitle() {
        ConversationDO existingConversation = conversationGroupService.findConversation(
                conversationId,
                userId
        );
        return existingConversation == null || StrUtil.isBlank(existingConversation.getTitle());
    }

    /**
     * 设置检索到的 chunks（用于 finish 事件返回）
     */
    @Override
    public void setRetrievedChunks(List<RetrievedChunk> chunks) {
        this.retrievedChunks = chunks;
        log.info("[StreamChatEventHandler] setRetrievedChunks 被调用, chunks 数量: {}", chunks != null ? chunks.size() : "null");
    }

    /**
     * 构造取消时的完成载荷（如果有内容则先落库）
     */
    private CompletionPayload buildCompletionPayloadOnCancel() {
        String content = answer.toString();
        String messageId = null;
        if (StrUtil.isNotBlank(content)) {
            try {
                String thinkingContent = thinking.isEmpty() ? null : thinking.toString();
                ChatMessage message = ChatMessage.assistant(content, thinkingContent, resolveThinkingDuration());
                messageId = memoryService.append(conversationId, userId, message);
            } catch (Exception e) {
                log.error("取消时持久化消息失败，conversationId：{}", conversationId, e);
            }
        }
        String title = resolveTitleForEvent();
        List<CompletionPayload.NoteReference> references = buildNoteReferences();
        return new CompletionPayload(String.valueOf(messageId), title, references);
    }

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

    @Override
    public void onError(Throwable t) {
        if (taskManager.isCancelled(taskId)) {
            return;
        }
        taskManager.unregister(taskId);
        sender.fail(t);
    }

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

    private Integer resolveThinkingDuration() {
        return thinkingDurationSeconds > 0 ? thinkingDurationSeconds : null;
    }

    private String resolveTitleForEvent() {
        if (!sendTitleOnComplete) {
            return null;
        }
        ConversationDO conversation = conversationGroupService.findConversation(conversationId, userId);
        if (conversation != null && StrUtil.isNotBlank(conversation.getTitle())) {
            return conversation.getTitle();
        }
        return "新对话";
    }

    private List<CompletionPayload.NoteReference> buildNoteReferences() {
        log.info("[StreamChatEventHandler] 开始构建笔记引用, retrievedChunks: {}",
                retrievedChunks != null ? retrievedChunks.size() : "null");

        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            log.info("[StreamChatEventHandler] retrievedChunks 为空，返回 null");
            return null;
        }

        double minScoreThreshold = searchChannelProperties.getNoteReference().getMinScoreThreshold();
        int maxCount = searchChannelProperties.getNoteReference().getMaxCount();

        List<CompletionPayload.NoteReference> references = retrievedChunks.stream()
                .filter(chunk -> {
                    boolean hasMetadata = chunk.getMetadata() != null;
                    boolean scoreAboveThreshold = chunk.getScore() >= minScoreThreshold;
                    log.debug("[StreamChatEventHandler] chunk id={}, score={}, hasMetadata={}, scoreAboveThreshold={}",
                            chunk.getId(), chunk.getScore(), hasMetadata, scoreAboveThreshold);
                    return hasMetadata && scoreAboveThreshold;
                })
                .map(chunk -> {
                    Long noteId = extractNoteId(chunk.getMetadata());
                    log.debug("[StreamChatEventHandler] chunk id={}, noteId={}", chunk.getId(), noteId);
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
                        (ref1, ref2) -> ref1.score() > ref2.score() ? ref1 : ref2  // 保留分数更高的
                ))
                .values()
                .stream()
                .sorted((r1, r2) -> Double.compare(r2.score(), r1.score()))  // 按分数降序排序
                .limit(maxCount)
                .toList();

        log.info("[StreamChatEventHandler] 构建笔记引用完成, 引用数量: {}, 阈值: {}, 最大数量: {}",
                references.size(), minScoreThreshold, maxCount);
        return references.isEmpty() ? null : references;
    }

    private Long extractNoteId(java.util.Map<String, Object> metadata) {
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

    private String extractTitle(java.util.Map<String, Object> metadata, Long noteId) {
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
}
