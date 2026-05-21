

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "IntentNodeUpdateRequest请求参数")
public class IntentNodeUpdateRequest {

@Schema(description = "name")
    private String name;
@Schema(description = "level")
    private Integer level;
@Schema(description = "parentCode")
    private String parentCode;
@Schema(description = "description")
    private String description;
@Schema(description = "examples")
    private List<String> examples;
@Schema(description = "collectionName")
    private String collectionName;
@Schema(description = "topK")
    private Integer topK;
@Schema(description = "kind")
    private Integer kind;
@Schema(description = "sortOrder")
    private Integer sortOrder;
@Schema(description = "enabled")
    private Integer enabled;
@Schema(description = "promptSnippet")
    private String promptSnippet;
@Schema(description = "promptTemplate")
    private String promptTemplate;
@Schema(description = "paramPromptTemplate")
    private String paramPromptTemplate;
}
