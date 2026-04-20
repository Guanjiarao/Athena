package athena.ground.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 博客问答引用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogAskReferenceDTO {

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 引用标题
     */
    private String title;

    /**
     * 引用片段
     */
    private String snippet;

    /**
     * 相关性得分
     */
    private Float score;
}
