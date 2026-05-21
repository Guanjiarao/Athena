

package com.nageoffer.ai.ragent.knowledge.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 知识库前端返回对象
 */
@Data
@Schema(description = "知识库前端返回对象")
public class KnowledgeBaseVO {

    /**
     * 知识库ID
     */
@Schema(description = "知识库前端返回对象")
    private String id;

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称")
    private String name;

    /**
     * 嵌入模型标识
     */
    @Schema(description = "嵌入模型标识")
    private String embeddingModel;

    /**
     * Milvus Collection 名称
     */
    @Schema(description = "Milvus Collection 名称")
    private String collectionName;

    /**
     * 文档数量
     */
    @Schema(description = "文档数量")
    private Long documentCount;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createdBy;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private Date updateTime;
}
