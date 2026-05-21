

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话更新请求类
 */
@Data
@Schema(description = "会话更新请求类")
public class ConversationUpdateRequest {

    /**
     * 会话标题
     */
@Schema(description = "会话更新请求类")
    private String title;
}
