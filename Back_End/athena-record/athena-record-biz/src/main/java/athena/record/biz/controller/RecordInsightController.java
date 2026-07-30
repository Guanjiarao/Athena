package athena.record.biz.controller;

import athena.athenaframework.result.Result;
import athena.record.biz.domain.dataobject.DailyRecord;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import athena.record.biz.domain.vo.MenstruationStatsVO;
import athena.record.biz.service.MenstruationCycleService;
import athena.record.biz.service.RecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/athena/record/internal/insight")
@RequiredArgsConstructor
public class RecordInsightController {

    private final MenstruationCycleService menstruationCycleService;
    private final RecordService recordService;

    @GetMapping("/cycle-stats")
    public Result<MenstruationStatsVO> getCycleStatsByUserId(@RequestParam Long userId) {
        try {
            return Result.ok(menstruationCycleService.getCycleStats(userId));
        } catch (Exception e) {
            log.error("[RecordInsightController] 查询周期统计失败, userId={}", userId, e);
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/prediction")
    public Result<MenstruationPredictionVO> getPredictionByUserId(@RequestParam Long userId) {
        try {
            return Result.ok(menstruationCycleService.getPrediction(userId));
        } catch (Exception e) {
            log.error("[RecordInsightController] 查询预测失败, userId={}", userId, e);
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/records")
    public Result<List<DailyRecord>> getRecordsByUserIdAndRange(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            return Result.ok(recordService.getDailyDetailsInRange(userId, startDate, endDate));
        } catch (Exception e) {
            log.error("[RecordInsightController] 查询记录范围失败, userId={}, startDate={}, endDate={}", userId, startDate, endDate, e);
            return Result.fail(e.getMessage());
        }
    }
}
