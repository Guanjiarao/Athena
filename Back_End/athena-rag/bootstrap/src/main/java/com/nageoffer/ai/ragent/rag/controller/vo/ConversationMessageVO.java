

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话消息视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "会话消息视图对象")
public class ConversationMessageVO {

    /**
     * 消息ID
     */
@Schema(description = "会话消息视图对象")
    private String id;

    /**
     * 会话ID
     */
    @Schema(description = "会话ID")
    private String conversationId;

    /**
     * 角色 (如: user, assistant)
     */
    @Schema(description = "角色 (如: user, assistant)")
    private String role;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String content;

    /**
     * 反馈值：1=点赞，-1=点踩，null=未反馈
     */
    @Schema(description = "反馈值：1=点赞，-1=点踩，null=未反馈")
    private Integer vote;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Date createTime;
}
