

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库 Chunk 更新请求
 */
@Data
@Schema(description = "知识库 Chunk 更新请求")
public class KnowledgeChunkUpdateRequest {

    /**
     * 分块正文内容
     */
@Schema(description = "知识库 Chunk 更新请求")
    private String content;
}
