

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
@Schema(description = "KnowledgeDocumentPageRequest请求参数")
public class KnowledgeDocumentPageRequest extends Page {

@Schema(description = "status")
    private String status;

@Schema(description = "keyword")
    private String keyword;
}
