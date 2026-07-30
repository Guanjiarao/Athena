package athena.insight.biz.service;

import athena.insight.biz.domain.dto.RecommendQueryDTO;
import athena.insight.biz.domain.vo.RecommendResultVO;

public interface RecommendationService {

    RecommendResultVO recommend(Long userId, RecommendQueryDTO request);
}
