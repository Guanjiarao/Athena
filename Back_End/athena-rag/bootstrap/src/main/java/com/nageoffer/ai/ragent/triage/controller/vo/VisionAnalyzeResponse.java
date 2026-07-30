

package com.nageoffer.ai.ragent.triage.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视觉分析代理响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视觉分析代理响应。")
public class VisionAnalyzeResponse {

@Schema(description = "content")
    private String content;

@Schema(description = "model")
    private String model;
}
