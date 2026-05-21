

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 关键词映射视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "关键词映射视图对象")
public class QueryTermMappingVO {

@Schema(description = "id")
    private String id;
@Schema(description = "sourceTerm")
    private String sourceTerm;
@Schema(description = "targetTerm")
    private String targetTerm;
@Schema(description = "matchType")
    private Integer matchType;
@Schema(description = "priority")
    private Integer priority;
@Schema(description = "enabled")
    private Boolean enabled;
@Schema(description = "remark")
    private String remark;
@Schema(description = "createTime")
    private Date createTime;
@Schema(description = "updateTime")
    private Date updateTime;
}
