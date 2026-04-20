package athena.insight.biz.domain.vo;

import lombok.Data;

@Data
public class UserInsightVO {

    private Long userId;

    private String healthFocusJson;

    private String contentFocusJson;

    private String riskTagsJson;

    private String recommendationReasonsJson;

    private String generatedAt;
}
