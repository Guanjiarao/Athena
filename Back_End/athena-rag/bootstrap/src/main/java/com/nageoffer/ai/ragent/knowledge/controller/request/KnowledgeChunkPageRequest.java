

package com.nageoffer.ai.ragent.knowledge.controller.request;



import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
@Schema(description = "KnowledgeChunkPageRequest请求参数")
public class KnowledgeChunkPageRequest extends Page {

@Schema(description = "enabled")
    private Integer enabled;
}
