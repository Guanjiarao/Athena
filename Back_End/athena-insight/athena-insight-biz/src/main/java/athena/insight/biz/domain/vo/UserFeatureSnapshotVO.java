package athena.insight.biz.domain.vo;

import lombok.Data;

@Data
public class UserFeatureSnapshotVO {

    private Long userId;

    private String baseFeatureJson;

    private String behaviorFeatureJson;

    private String healthFeatureJson;

    private String generatedAt;
}
