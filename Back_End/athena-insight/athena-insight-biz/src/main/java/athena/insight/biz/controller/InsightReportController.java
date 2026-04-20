package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.insight.biz.domain.dto.ReportQueryDTO;
import athena.insight.biz.service.ReportService;
import athena.insight.biz.service.UserInsightService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/athena/insight")
public class InsightReportController {

    @Resource
    private ReportService reportService;

    @Resource
    private UserInsightService userInsightService;

    @GetMapping("/report")
    public Result report() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到报告请求, userId={}", userId);

        ReportQueryDTO request = new ReportQueryDTO();
        request.setUserId(userId);
        return Result.ok(reportService.generateReport(request));
    }

    @GetMapping("/insight")
    public Result insight() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到洞察查询请求, userId={}", userId);
        return Result.ok(userInsightService.getInsight(userId));
    }

    @PostMapping("/insight/refresh")
    public Result refreshInsight() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到洞察刷新请求, userId={}", userId);
        return Result.ok(userInsightService.refreshInsight(userId));
    }
}
