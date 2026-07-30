package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "联调刷新结果")
public class InsightDebugRefreshVO {

    @Schema(description = "用户特征快照")
    private UserFeatureSnapshotVO featureSnapshot;

    @Schema(description = "用户洞察结果")
    private UserInsightVO insight;

    @Schema(description = "用户分析报告")
    private UserAnalysisReportVO report;
}
