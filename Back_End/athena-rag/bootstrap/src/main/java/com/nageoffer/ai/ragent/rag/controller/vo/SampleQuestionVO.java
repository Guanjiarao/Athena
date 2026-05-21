

package com.nageoffer.ai.ragent.rag.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 示例问题视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "示例问题视图对象")
public class SampleQuestionVO {

@Schema(description = "id")
    private String id;
@Schema(description = "title")
    private String title;
@Schema(description = "description")
    private String description;
@Schema(description = "question")
    private String question;
@Schema(description = "createTime")
    private Date createTime;
@Schema(description = "updateTime")
    private Date updateTime;
}
