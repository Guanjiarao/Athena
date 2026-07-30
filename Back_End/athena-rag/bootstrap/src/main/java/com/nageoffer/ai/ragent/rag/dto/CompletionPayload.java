

package com.nageoffer.ai.ragent.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 模型回复完成事件载荷
 *
 * @param messageId 消息ID（字符串，避免前端精度丢失）
 * @param title     会话标题（可选）
 * @param references 笔记引用列表（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletionPayload(String messageId, String title, List<NoteReference> references) {

    /**
     * 笔记引用
     *
     * @param noteId  笔记ID
     * @param title   笔记标题
     * @param snippet 文本片段
     * @param score   相关性得分
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoteReference(Long noteId, String title, String snippet, Float score) {
    }
}
