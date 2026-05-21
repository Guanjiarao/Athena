

package com.nageoffer.ai.ragent.rag.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 示例问题分页查询请求
 */
@Data
@Schema(description = "示例问题分页查询请求")
public class SampleQuestionPageRequest extends Page {

    /**
     * 关键词（支持匹配标题、描述、问题内容）
     */
@Schema(description = "示例问题分页查询请求")
    private String keyword;
}
