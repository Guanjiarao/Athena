

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识库 Chunk 批量操作请求
 */
@Data
@Schema(description = "知识库 Chunk 批量操作请求")
public class KnowledgeChunkBatchRequest {

    /**
     * Chunk ID 列表（可选，不传则操作文档下所有 chunk）
     */
@Schema(description = "知识库 Chunk 批量操作请求")
    private List<String> chunkIds;
}
