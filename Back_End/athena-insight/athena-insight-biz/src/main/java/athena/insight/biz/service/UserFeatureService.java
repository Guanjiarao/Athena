package athena.insight.biz.service;

import athena.insight.biz.domain.dto.FeatureRefreshDTO;
import athena.insight.biz.domain.vo.UserFeatureSnapshotVO;

public interface UserFeatureService {

    UserFeatureSnapshotVO getSnapshot(Long userId);

    UserFeatureSnapshotVO refreshSnapshot(FeatureRefreshDTO request);
}
