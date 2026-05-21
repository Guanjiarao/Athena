

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * RAG Trace 运行记录
 */
@Data
@Builder
@Schema(description = "RAG Trace 运行记录")
public class RagTraceRunVO {

@Schema(description = "traceId")
    private String traceId;

@Schema(description = "traceName")
    private String traceName;

@Schema(description = "entryMethod")
    private String entryMethod;

@Schema(description = "conversationId")
    private String conversationId;

@Schema(description = "taskId")
    private String taskId;

@Schema(description = "userId")
    private String userId;

@Schema(description = "username")
    private String username;

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
