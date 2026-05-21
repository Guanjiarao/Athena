

package com.nageoffer.ai.ragent.ingestion.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import com.nageoffer.ai.ragent.ingestion.domain.context.NodeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 知识库摄取任务视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "知识库摄取任务视图对象")
public class IngestionTaskVO {

    /**
     * 任务ID
     */
@Schema(description = "知识库摄取任务视图对象")
    private String id;

    /**
     * 流水线ID
     */
    @Schema(description = "流水线ID")
    private String pipelineId;

    /**
     * 数据源类型
     */
    @Schema(description = "数据源类型")
    private String sourceType;

    /**
     * 数据源位置
     */
    @Schema(description = "数据源位置")
    private String sourceLocation;

    /**
     * 数据源文件名
     */
    @Schema(description = "数据源文件名")
    private String sourceFileName;

    /**
     * 任务状态
     */
    @Schema(description = "任务状态")
    private String status;

    /**
     * 分片数量
     */
    @Schema(description = "分片数量")
    private Integer chunkCount;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 节点运行日志
     */
    @Schema(description = "节点运行日志")
    private List<NodeLog> logs;

    /**
     * 元数据信息
     */
    @Schema(description = "元数据信息")
    private Map<String, Object> metadata;

    /**
     * 任务开始时间
     */
    @Schema(description = "任务开始时间")
    private Date startedAt;

    /**
     * 任务完成时间
     */
    @Schema(description = "任务完成时间")
    private Date completedAt;

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
