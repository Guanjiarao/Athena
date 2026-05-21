

package com.nageoffer.ai.ragent.ingestion.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * 摄取任务节点视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "摄取任务节点视图对象")
public class IngestionTaskNodeVO {

    /**
     * ID
     */
@Schema(description = "摄取任务节点视图对象")
    private String id;

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private String taskId;

    /**
     * 流水线ID
     */
    @Schema(description = "流水线ID")
    private String pipelineId;

    /**
     * 节点ID
     */
    @Schema(description = "节点ID")
    private String nodeId;

    /**
     * 节点类型
     * 如 fetcher、parser、chunker 等
     */
    @Schema(description = "节点类型")
    private String nodeType;

    /**
     * 节点排序
     */
    @Schema(description = "节点排序")
    private Integer nodeOrder;

    /**
     * 状态 (如: success, failed, skipped)
     */
    @Schema(description = "状态 (如: success, failed, skipped)")
    private String status;

    /**
     * 耗时（毫秒）
     */
    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    /**
     * 消息
     */
    @Schema(description = "消息")
    private String message;

    /**
     * 错误消息
     */
    @Schema(description = "错误消息")
    private String errorMessage;

    /**
     * 输出结果
     */
    @Schema(description = "输出结果")
    private Map<String, Object> output;

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
