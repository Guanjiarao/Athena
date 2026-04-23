package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户分析报告")
public class UserAnalysisReportVO {

    @Schema(description = "当前模式类型", example = "1")
    private Integer currentModeType;

    @Schema(description = "平均周期天数", example = "28")
    private Integer averageCycleLength;

    @Schema(description = "平均持续天数", example = "5")
    private Integer averageDurationDays;

    @Schema(description = "分析摘要")
    private String summary;

    @Schema(description = "摘要来源，例如 system / ai")
    private String summarySource;

    @Schema(description = "健康关注点列表")
    private List<String> healthFocuses;

    @Schema(description = "内容关注点列表")
    private List<String> contentFocuses;

    @Schema(description = "风险标签列表")
    private List<String> riskTags;

    @Schema(description = "推荐主题列表")
    private List<String> recommendTopics;

    @Schema(description = "推荐阅读内容")
    private List<RecommendItemVO> readingSuggestions;
}
