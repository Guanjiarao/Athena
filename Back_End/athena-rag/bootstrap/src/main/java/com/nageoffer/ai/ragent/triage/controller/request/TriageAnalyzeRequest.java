

package com.nageoffer.ai.ragent.triage.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 就医助手分析请求。
 *
 * <p>sessionId 可由前端带入，用于串联一轮问诊上下文；
 * 如果前端不传，后端会自动生成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "就医助手分析请求。")
public class TriageAnalyzeRequest {

    /**
     * 会话 ID，可为空。
     */
@Schema(description = "就医助手分析请求。")
    private String sessionId;

    /**
     * 用户原始输入。
     */
    @NotBlank(message = "userInput 不能为空")
    @Schema(description = "用户原始输入。")
    private String userInput;
}
