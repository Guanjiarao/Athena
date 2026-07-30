package athena.insight.biz.service;

import athena.insight.biz.domain.dto.ReportQueryDTO;
import athena.insight.biz.domain.vo.UserAnalysisReportVO;

public interface ReportService {

    UserAnalysisReportVO generateReport(ReportQueryDTO request);
}
