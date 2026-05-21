

package com.nageoffer.ai.ragent.ingestion.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 数据摄取管道视图对象
 */
@Data
@Schema(description = "数据摄取管道视图对象")
public class IngestionPipelineVO {

    /**
     * 管道ID
     */
@Schema(description = "数据摄取管道视图对象")
    private String id;

    /**
     * 管道名称
     */
    @Schema(description = "管道名称")
    private String name;

    /**
     * 管道描述
     */
    @Schema(description = "管道描述")
    private String description;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createdBy;

    /**
     * 管道节点列表
     */
    @Schema(description = "管道节点列表")
    private List<IngestionPipelineNodeVO> nodes;

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
