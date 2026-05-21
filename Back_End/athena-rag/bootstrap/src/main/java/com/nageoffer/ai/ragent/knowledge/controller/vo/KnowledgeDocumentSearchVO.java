

package com.nageoffer.ai.ragent.knowledge.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文档搜索返回对象
 */
@Data
@Schema(description = "文档搜索返回对象")
public class KnowledgeDocumentSearchVO {

    /**
     * 文档ID
     */
@Schema(description = "文档搜索返回对象")
    private String id;

    /**
     * 知识库ID
     */
    @Schema(description = "知识库ID")
    private String kbId;

    /**
     * 文档名称
     */
    @Schema(description = "文档名称")
    private String docName;

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称")
    private String kbName;
}
