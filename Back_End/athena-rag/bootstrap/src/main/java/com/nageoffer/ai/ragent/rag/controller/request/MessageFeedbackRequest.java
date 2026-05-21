

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话消息反馈请求
 */
@Data
@Schema(description = "会话消息反馈请求")
public class MessageFeedbackRequest {

    /**
     * 反馈值：1=点赞，-1=点踩
     */
@Schema(description = "会话消息反馈请求")
    private Integer vote;

    /**
     * 反馈原因（可选）
     */
    @Schema(description = "反馈原因（可选）")
    private String reason;

    /**
     * 补充说明（可选）
     */
    @Schema(description = "补充说明（可选）")
    private String comment;
}
