

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "KnowledgeBaseCreateRequest请求参数")
public class KnowledgeBaseCreateRequest {

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称")
    private String name;

    /**
     * 嵌入模型，如 qwen3-embedding:8b-fp16
     */
    @Schema(description = "嵌入模型，如 qwen3-embedding:8b-fp16")
    private String embeddingModel;

    /**
     * Milvus Collection 名称
     */
    @Schema(description = "Milvus Collection 名称")
    private String collectionName;
}
