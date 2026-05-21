

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 关键词映射分页查询请求
 */
@Data
@Schema(description = "关键词映射分页查询请求")
public class QueryTermMappingPageRequest extends Page {

    /**
     * 关键词（支持匹配 sourceTerm/targetTerm）
     */
@Schema(description = "关键词映射分页查询请求")
    private String keyword;
}
