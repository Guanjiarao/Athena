

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * RAG Trace 节点明细
 */
@Data
@Builder
@Schema(description = "RAG Trace 节点明细")
public class RagTraceNodeVO {

@Schema(description = "traceId")
    private String traceId;

@Schema(description = "nodeId")
    private String nodeId;

@Schema(description = "parentNodeId")
    private String parentNodeId;

@Schema(description = "depth")
    private Integer depth;

@Schema(description = "nodeType")
    private String nodeType;

@Schema(description = "nodeName")
    private String nodeName;

@Schema(description = "className")
    private String className;

@Schema(description = "methodName")
    private String methodName;

@Schema(description = "status")
    private String status;

@Schema(description = "errorMessage")
    private String errorMessage;

@Schema(description = "durationMs")
    private Long durationMs;

@Schema(description = "startTime")
    private Date startTime;

@Schema(description = "endTime")
    private Date endTime;
}
