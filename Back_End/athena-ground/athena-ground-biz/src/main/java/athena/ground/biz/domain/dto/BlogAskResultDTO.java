package athena.ground.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 博客问答响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogAskResultDTO {

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 实际生效年龄
     */
    private Integer resolvedAge;

    /**
     * 命中的知识库编码
     */
    private List<String> kbCodes;

    /**
     * 引用笔记
     */
    private List<BlogAskReferenceDTO> references;
}
