package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.insight.biz.domain.dto.ReportQueryDTO;
import athena.insight.biz.service.ReportService;
import athena.insight.biz.service.UserInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "洞察报告接口")
@RestController
@RequestMapping("/athena/insight")
public class InsightReportController {

    @Resource
    private ReportService reportService;

    @Resource
    private UserInsightService userInsightService;

    @Operation(summary = "获取用户分析报告")
    @GetMapping("/report")
    public Result report() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到报告请求, userId={}", userId);

        ReportQueryDTO request = new ReportQueryDTO();
        request.setUserId(userId);
        return Result.ok(reportService.generateReport(request));
    }

    @Operation(summary = "查询用户洞察")
    @GetMapping("/insight")
    public Result insight() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到洞察查询请求, userId={}", userId);
        return Result.ok(userInsightService.getInsight(userId));
    }

    @Operation(summary = "刷新用户洞察")
    @PostMapping("/insight/refresh")
    public Result refreshInsight() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightReport] 收到洞察刷新请求, userId={}", userId);
        return Result.ok(userInsightService.refreshInsight(userId));
    }
}
