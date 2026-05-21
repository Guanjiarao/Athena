

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库 Chunk 创建请求
 */
@Data
@Schema(description = "知识库 Chunk 创建请求")
public class KnowledgeChunkCreateRequest {

    /**
     * 分块正文内容
     */
@Schema(description = "知识库 Chunk 创建请求")
    private String content;

    /**
     * 下标
     */
    @Schema(description = "下标")
    private Integer index;

    /**
     * 分块 ID
     */
    @Schema(description = "分块 ID")
    private String chunkId;
}
