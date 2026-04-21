package athena.insight.biz.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserAnalysisReportVO {

    private Integer currentModeType;

    private Integer averageCycleLength;

    private Integer averageDurationDays;

    private String summary;

    private String summarySource;

    private List<String> healthFocuses;

    private List<String> contentFocuses;

    private List<String> riskTags;

    private List<String> recommendTopics;

    private List<RecommendItemVO> readingSuggestions;
}
