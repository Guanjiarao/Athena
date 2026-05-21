

package com.nageoffer.ai.ragent.triage.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 就医助手统一分析响应。
 *
 * <p>按照前端约定，接口始终返回统一四元组：
 * action + data + message + riskLevel。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "就医助手统一分析响应。")
public class TriageAnalyzeResponse {

    /**
     * 编排器动作。
     */
@Schema(description = "就医助手统一分析响应。")
    private String action;

    /**
     * 动作对应的结构化数据载荷。
     */
    @Schema(description = "动作对应的结构化数据载荷。")
    private Object data;

    /**
     * 前端可直接展示的摘要消息。
     */
    @Schema(description = "前端可直接展示的摘要消息。")
    private String message;

    /**
     * 风险等级；未评估时为 0。
     */
    @Schema(description = "风险等级；未评估时为 0。")
    private Integer riskLevel;
}
