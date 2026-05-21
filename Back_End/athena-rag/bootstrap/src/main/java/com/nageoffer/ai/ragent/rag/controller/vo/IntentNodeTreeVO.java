

package com.nageoffer.ai.ragent.rag.controller.vo;



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
@Schema(description = "IntentNodeTreeVO返回对象")
public class IntentNodeTreeVO {

@Schema(description = "id")
    private String id;
@Schema(description = "intentCode")
    private String intentCode;
@Schema(description = "name")
    private String name;
@Schema(description = "level")
    private Integer level;
@Schema(description = "parentCode")
    private String parentCode;
@Schema(description = "description")
    private String description;
@Schema(description = "examples")
    private String examples;
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

    /**
     * MCP 工具 ID（仅对 kind=2 有意义）
     */
    @Schema(description = "MCP 工具 ID（仅对 kind=2 有意义）")
    private String mcpToolId;

    /**
     * 短规则片段（可选）
     */
    @Schema(description = "短规则片段（可选）")
    private String promptSnippet;

    /**
     * 场景用的完整 Prompt 模板（可选）
     */
    @Schema(description = "场景用的完整 Prompt 模板（可选）")
    private String promptTemplate;

    /**
     * 参数提取提示词模板（MCP模式专属）
     */
    @Schema(description = "参数提取提示词模板（MCP模式专属）")
    private String paramPromptTemplate;

@Schema(description = "children")
    private List<IntentNodeTreeVO> children;
}
