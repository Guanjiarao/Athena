package athena.insight.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户特征刷新请求")
public class FeatureRefreshDTO {

    @Schema(description = "用户ID，不传时默认取当前登录用户")
    private Long userId;

    @Schema(description = "是否强制刷新", example = "false")
    private Boolean forceRefresh;
}
