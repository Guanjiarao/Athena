package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "推荐内容项")
public class RecommendItemVO {

    @Schema(description = "内容ID", example = "10001")
    private Long noteId;

    @Schema(description = "推荐类型", example = "1")
    private Byte type;

    @Schema(description = "内容标题")
    private String title;

    @Schema(description = "封面图地址")
    private String coverUrl;

    @Schema(description = "作者ID", example = "20001")
    private Long authorId;

    @Schema(description = "话题标签列表")
    private List<String> topics;

    @Schema(description = "推荐原因")
    private String reason;

    @Schema(description = "推荐分数", example = "0.92")
    private Double score;
}
