package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.insight.biz.domain.dto.FeatureRefreshDTO;
import athena.insight.biz.domain.dto.NoteFeatureRefreshDTO;
import athena.insight.biz.domain.dto.ReportQueryDTO;
import athena.insight.biz.domain.vo.InsightDebugRefreshVO;
import athena.insight.biz.service.NoteFeatureService;
import athena.insight.biz.service.ReportService;
import athena.insight.biz.service.UserFeatureService;
import athena.insight.biz.service.UserInsightService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/athena/insight")
public class InsightFeatureController {

    @Resource
    private UserFeatureService userFeatureService;

    @Resource
    private UserInsightService userInsightService;

    @Resource
    private NoteFeatureService noteFeatureService;

    @Resource
    private ReportService reportService;

    @GetMapping("/feature")
    public Result feature() {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightFeature] 收到特征查询请求, userId={}", userId);
        return Result.ok(userFeatureService.getSnapshot(userId));
    }

    @PostMapping("/feature/refresh")
    public Result refresh(@RequestBody(required = false) FeatureRefreshDTO request) {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightFeature] 收到特征刷新请求, userId={}", userId);

        FeatureRefreshDTO actualRequest = request == null ? new FeatureRefreshDTO() : request;
        if (actualRequest.getUserId() == null) {
            actualRequest.setUserId(userId);
        }
        return Result.ok(userFeatureService.refreshSnapshot(actualRequest));
    }

    @PostMapping("/note-feature/refresh")
    public Result refreshNoteFeature(@RequestBody(required = false) NoteFeatureRefreshDTO request) {
        NoteFeatureRefreshDTO actualRequest = request == null ? new NoteFeatureRefreshDTO() : request;
        if (actualRequest.getNoteId() != null) {
            log.info("[InsightFeature] 刷新单条内容特征, noteId={}", actualRequest.getNoteId());
            return Result.ok(noteFeatureService.refreshByNoteId(actualRequest.getNoteId()));
        }
        log.info("[InsightFeature] 刷新公共内容池特征, pageNum={}, pageSize={}", actualRequest.getPageNum(), actualRequest.getPageSize());
        return Result.ok(noteFeatureService.refreshPublicPool(actualRequest.getPageNum(), actualRequest.getPageSize()));
    }

    @DeleteMapping("/note-feature")
    public Result deleteNoteFeature(@RequestParam("noteId") Long noteId) {
        log.info("[InsightFeature] 删除单条内容特征, noteId={}", noteId);
        noteFeatureService.deleteByNoteId(noteId);
        return Result.ok();
    }

    @PostMapping("/debug/refresh-all")
    public Result refreshAll(@RequestBody(required = false) FeatureRefreshDTO request) {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightFeature] 收到联调刷新请求, userId={}", userId);

        FeatureRefreshDTO actualRequest = request == null ? new FeatureRefreshDTO() : request;
        if (actualRequest.getUserId() == null) {
            actualRequest.setUserId(userId);
        }

        InsightDebugRefreshVO vo = new InsightDebugRefreshVO();
        vo.setFeatureSnapshot(userFeatureService.refreshSnapshot(actualRequest));
        vo.setInsight(userInsightService.refreshInsight(actualRequest.getUserId()));

        ReportQueryDTO reportQueryDTO = new ReportQueryDTO();
        reportQueryDTO.setUserId(actualRequest.getUserId());
        vo.setReport(reportService.generateReport(reportQueryDTO));
        return Result.ok(vo);
    }
}
