

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 示例问题创建请求
 */
@Data
@Schema(description = "示例问题创建请求")
public class SampleQuestionCreateRequest {

    /**
     * 展示标题（可选）
     */
@Schema(description = "示例问题创建请求")
    private String title;

    /**
     * 描述或提示（可选）
     */
    @Schema(description = "描述或提示（可选）")
    private String description;

    /**
     * 示例问题内容
     */
    @Schema(description = "示例问题内容")
    private String question;
}
