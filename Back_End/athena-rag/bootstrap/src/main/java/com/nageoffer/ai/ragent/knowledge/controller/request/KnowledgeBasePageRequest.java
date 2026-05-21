

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 知识库分页查询请求
 */
@Data
@Schema(description = "知识库分页查询请求")
public class KnowledgeBasePageRequest extends Page {

    /**
     * 知识库名称（支持模糊匹配）
     */
@Schema(description = "知识库分页查询请求")
    private String name;
}
