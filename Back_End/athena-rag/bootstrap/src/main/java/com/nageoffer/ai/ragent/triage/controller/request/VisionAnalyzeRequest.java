

package com.nageoffer.ai.ragent.triage.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端视觉分析代理请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "前端视觉分析代理请求。")
public class VisionAnalyzeRequest {

    /**
     * 视觉模型名，可为空，后端会使用默认值。
     */
@Schema(description = "前端视觉分析代理请求。")
    private String model;

    /**
     * 已可公网访问的图片 URL。
     */
    @NotBlank(message = "imageUrl 不能为空")
    @Schema(description = "已可公网访问的图片 URL。")
    private String imageUrl;

    /**
     * 用户侧任务 Prompt。
     */
    @NotBlank(message = "prompt 不能为空")
    @Schema(description = "用户侧任务 Prompt。")
    private String prompt;
}
