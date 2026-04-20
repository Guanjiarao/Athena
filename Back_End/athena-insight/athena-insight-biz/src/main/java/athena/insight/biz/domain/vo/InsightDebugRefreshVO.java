package athena.insight.biz.domain.vo;

import lombok.Data;

@Data
public class InsightDebugRefreshVO {

    private UserFeatureSnapshotVO featureSnapshot;

    private UserInsightVO insight;

    private UserAnalysisReportVO report;
}
