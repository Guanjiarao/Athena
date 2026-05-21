

package com.nageoffer.ai.ragent.ingestion.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@Schema(description = "IngestionPipelineNodeVO返回对象")
public class IngestionPipelineNodeVO {

@Schema(description = "id")
    private String id;

@Schema(description = "nodeId")
    private String nodeId;

@Schema(description = "nodeType")
    private String nodeType;

@Schema(description = "settings")
    private JsonNode settings;

@Schema(description = "condition")
    private JsonNode condition;

@Schema(description = "nextNodeId")
    private String nextNodeId;
}
