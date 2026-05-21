

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "IntentNodeBatchRequest请求参数")
public class IntentNodeBatchRequest {

    /**
     * 节点 ID 列表
     */
    @Schema(description = "节点 ID 列表")
    private List<String> ids;
}
