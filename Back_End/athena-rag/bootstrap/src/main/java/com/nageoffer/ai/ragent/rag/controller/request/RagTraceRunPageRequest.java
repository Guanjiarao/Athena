

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * RAG Trace 运行记录分页请求
 */
@Data
@Schema(description = "RAG Trace 运行记录分页请求")
public class RagTraceRunPageRequest extends Page {

@Schema(description = "traceId")
    private String traceId;

@Schema(description = "conversationId")
    private String conversationId;

@Schema(description = "taskId")
    private String taskId;

@Schema(description = "status")
    private String status;
}
