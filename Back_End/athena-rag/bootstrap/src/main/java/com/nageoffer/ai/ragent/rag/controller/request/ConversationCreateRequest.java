

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话创建请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "会话创建请求")
public class ConversationCreateRequest {

    /**
     * 会话ID
     */
@Schema(description = "会话创建请求")
    private String conversationId;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private String userId;

    /**
     * 用户问题
     */
    @Schema(description = "用户问题")
    private String question;

    /**
     * 最后更新时间
     */
    @Schema(description = "最后更新时间")
    private Date lastTime;

}
