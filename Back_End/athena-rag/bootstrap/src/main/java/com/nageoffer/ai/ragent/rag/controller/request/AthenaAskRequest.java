

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Athena 问答请求。
 *
 * @deprecated 该请求对象仅服务于 Athena 早期 `athena-ground -> athena-rag` 过渡接口，
 * 当前问答主链路已切换到经网关访问的 RAG V3 通用接口。
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Athena 问答请求。")
public class AthenaAskRequest {

    /**
     * 用户问题
     */
@Schema(description = "Athena 问答请求。")
    private String question;

    /**
     * 用户年龄，可为空
     */
    @Schema(description = "用户年龄，可为空")
    private Integer age;
}
