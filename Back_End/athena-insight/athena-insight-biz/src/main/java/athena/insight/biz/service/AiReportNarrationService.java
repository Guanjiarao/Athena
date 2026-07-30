package athena.insight.biz.service;

import athena.insight.biz.domain.vo.UserAnalysisReportVO;

public interface AiReportNarrationService {

    String generateSummary(UserAnalysisReportVO reportVO, String fallbackSummary);
}
