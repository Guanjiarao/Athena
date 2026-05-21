

package com.nageoffer.ai.ragent.knowledge.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库 Chunk 视图对象
 */
@Data
@Schema(description = "知识库 Chunk 视图对象")
public class KnowledgeChunkVO {

    /**
     * ID
     */
@Schema(description = "知识库 Chunk 视图对象")
    private String id;

    /**
     * 知识库ID
     */
    @Schema(description = "知识库ID")
    private String kbId;

    /**
     * 文档ID
     */
    @Schema(description = "文档ID")
    private String docId;

    /**
     * 分块序号（从0开始）
     */
    @Schema(description = "分块序号（从0开始）")
    private Integer chunkIndex;

    /**
     * 分块正文内容
     */
    @Schema(description = "分块正文内容")
    private String content;

    /**
     * 内容哈希（用于幂等/去重）
     */
    @Schema(description = "内容哈希（用于幂等/去重）")
    private String contentHash;

    /**
     * 字符数
     */
    @Schema(description = "字符数")
    private Integer charCount;

    /**
     * Token数
     */
    @Schema(description = "Token数")
    private Integer tokenCount;

    /**
     * 是否启用 0：禁用 1：启用
     */
    @Schema(description = "是否启用 0：禁用 1：启用")
    private Integer enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
