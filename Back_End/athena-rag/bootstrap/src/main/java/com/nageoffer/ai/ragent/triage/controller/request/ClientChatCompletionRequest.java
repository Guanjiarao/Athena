

package com.nageoffer.ai.ragent.triage.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端文本补全代理请求。
 *
 * <p>该请求用于替代客户端直接持有 API Key 的模式，
 * 让文本类大模型请求统一收口到后端。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "前端文本补全代理请求。")
public class ClientChatCompletionRequest {

    /**
     * 前端发送的消息序列。
     */
    @Builder.Default
    @NotEmpty(message = "messages 不能为空")
    private List<ClientChatMessage> messages = new ArrayList<>();

    /**
     * 是否希望模型尽量返回 JSON。
     */
@Schema(description = "前端文本补全代理请求。")
    private Boolean jsonMode;

    /**
     * 前端兼容字段，当前由后端接管模型选择，因此仅保留不强依赖。
     */
    @Schema(description = "前端兼容字段，当前由后端接管模型选择，因此仅保留不强依赖。")
    private String model;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientChatMessage {

        /**
         * system / user / assistant
         */
        @Schema(description = "system / user / assistant")
        private String role;

        /**
         * 文本内容。
         */
        @Schema(description = "文本内容。")
        private String content;
    }
}
