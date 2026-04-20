package athena.ground.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 博客问答请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogAskDTO {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 用户年龄，可为空
     */
    private Integer age;
}
