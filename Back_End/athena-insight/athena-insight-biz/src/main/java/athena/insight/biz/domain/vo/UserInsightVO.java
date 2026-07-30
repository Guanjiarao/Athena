package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户洞察结果")
public class UserInsightVO {

    @Schema(description = "用户ID", example = "10001")
    private Long userId;

    @Schema(description = "健康关注点 JSON")
    private String healthFocusJson;

    @Schema(description = "内容关注点 JSON")
    private String contentFocusJson;

    @Schema(description = "风险标签 JSON")
    private String riskTagsJson;

    @Schema(description = "推荐原因 JSON")
    private String recommendationReasonsJson;

    @Schema(description = "生成时间")
    private String generatedAt;
}
