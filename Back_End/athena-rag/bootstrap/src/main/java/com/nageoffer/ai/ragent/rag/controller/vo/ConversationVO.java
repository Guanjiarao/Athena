

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "会话视图对象")
public class ConversationVO {

    /**
     * 会话ID
     */
@Schema(description = "会话视图对象")
    private String conversationId;

    /**
     * 会话标题
     */
    @Schema(description = "会话标题")
    private String title;

    /**
     * 最后活动时间
     */
    @Schema(description = "最后活动时间")
    private Date lastTime;
}
