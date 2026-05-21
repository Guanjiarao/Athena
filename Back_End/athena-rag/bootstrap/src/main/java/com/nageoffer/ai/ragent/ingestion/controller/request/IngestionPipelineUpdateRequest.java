

package com.nageoffer.ai.ragent.ingestion.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 摄取管道更新请求对象
 * 用于接收更新现有摄取管道的请求参数，包括管道名称、描述及节点配置列表
 */
@Data
@Schema(description = "摄取管道更新请求对象")
public class IngestionPipelineUpdateRequest {

    /**
     * 管道名称
     */
@Schema(description = "摄取管道更新请求对象")
    private String name;

    /**
     * 管道描述信息
     */
    @Schema(description = "管道描述信息")
    private String description;

    /**
     * 管道节点配置列表
     */
    @Schema(description = "管道节点配置列表")
    private List<IngestionPipelineNodeRequest> nodes;
}
