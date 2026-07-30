package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户特征快照")
public class UserFeatureSnapshotVO {

    @Schema(description = "用户ID", example = "10001")
    private Long userId;

    @Schema(description = "基础特征 JSON")
    private String baseFeatureJson;

    @Schema(description = "行为特征 JSON")
    private String behaviorFeatureJson;

    @Schema(description = "健康特征 JSON")
    private String healthFeatureJson;

    @Schema(description = "生成时间")
    private String generatedAt;
}
