package athena.insight.biz.domain.dto;

import lombok.Data;

@Data
public class FeatureRefreshDTO {

    private Long userId;

    private Boolean forceRefresh;
}
