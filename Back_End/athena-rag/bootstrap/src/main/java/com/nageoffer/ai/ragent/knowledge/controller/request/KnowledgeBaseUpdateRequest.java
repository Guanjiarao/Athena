

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "KnowledgeBaseUpdateRequest请求参数")
public class KnowledgeBaseUpdateRequest {

@Schema(description = "id")
    private String id;

    /**
     * 知识库名称（可修改）
     */
    @Schema(description = "知识库名称（可修改）")
    private String name;

    /**
     * 嵌入模型（有文档分块后禁止修改）
     */
    @Schema(description = "嵌入模型（有文档分块后禁止修改）")
    private String embeddingModel;
}
