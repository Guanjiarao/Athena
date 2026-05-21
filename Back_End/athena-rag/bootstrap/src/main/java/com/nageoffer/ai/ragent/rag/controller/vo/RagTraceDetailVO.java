

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG Trace 详情
 */
@Data
@Builder
@Schema(description = "RAG Trace 详情")
public class RagTraceDetailVO {

@Schema(description = "run")
    private RagTraceRunVO run;

@Schema(description = "nodes")
    private List<RagTraceNodeVO> nodes;
}
