package athena.insight.biz.service;

import athena.insight.biz.domain.vo.UserInsightVO;

public interface UserInsightService {

    UserInsightVO getInsight(Long userId);

    UserInsightVO refreshInsight(Long userId);
}
