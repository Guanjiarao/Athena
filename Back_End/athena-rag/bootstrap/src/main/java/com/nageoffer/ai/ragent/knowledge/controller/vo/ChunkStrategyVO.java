

package com.nageoffer.ai.ragent.knowledge.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Schema(description = "ChunkStrategyVO返回对象")
public class ChunkStrategyVO {

@Schema(description = "value")
    private String value;

@Schema(description = "label")
    private String label;

@Schema(description = "defaultConfig")
    private Map<String, Integer> defaultConfig;
}
