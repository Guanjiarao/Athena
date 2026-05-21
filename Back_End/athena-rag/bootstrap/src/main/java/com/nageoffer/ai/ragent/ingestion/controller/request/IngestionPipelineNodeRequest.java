

package com.nageoffer.ai.ragent.ingestion.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 摄取管道节点请求对象
 * 用于接收管道中单个节点的配置信息，包括节点标识、类型、设置参数及执行条件
 */
@Data
@Schema(description = "摄取管道节点请求对象")
public class IngestionPipelineNodeRequest {

    /**
     * 节点的唯一标识符
     */
@Schema(description = "摄取管道节点请求对象")
    private String nodeId;

    /**
     * 节点类型
     * 如 fetcher、parser、chunker、indexer 等
     */
    @Schema(description = "节点类型")
    private String nodeType;

    /**
     * 节点的配置参数
     * 不同类型的节点有不同的配置结构
     */
    @Schema(description = "节点的配置参数")
    private JsonNode settings;

    /**
     * 节点执行的条件表达式
     * 满足条件时才执行该节点
     */
    @Schema(description = "节点执行的条件表达式")
    private JsonNode condition;

    /**
     * 下一个节点ID
     * 用于定义管道中节点的执行顺序
     */
    @Schema(description = "下一个节点ID")
    private String nextNodeId;
}
