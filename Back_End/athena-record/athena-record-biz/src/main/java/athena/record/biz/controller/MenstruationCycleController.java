package athena.record.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.record.biz.domain.dto.MenstruationStartDTO;
import athena.record.biz.domain.dto.MenstruationUpdateDTO;
import athena.record.biz.domain.vo.MenstruationCycleVO;
import athena.record.biz.domain.vo.MenstruationMonthVO;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import athena.record.biz.domain.vo.MenstruationStatsVO;
import athena.record.biz.service.MenstruationCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经期记录与周期管理接口
 * 提供经期周期新增、编辑、删除、查询、统计、月视图、预测等功能
 *
 * @author Athena
 * @date 2026-01-01
 */
@Slf4j
@RestController
@RequestMapping("/athena/menstruation")
@RequiredArgsConstructor
public class MenstruationCycleController {

    private final MenstruationCycleService menstruationCycleService;

    /**
     * 新增经期周期记录
     *
     * @param dto 经期周期信息
     * @return 生成后的经期周期信息
     */
    @PostMapping("/start")
    public Result<MenstruationCycleVO> startMenstruation(@RequestBody MenstruationStartDTO dto) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.startMenstruation(userId, dto));
        } catch (Exception e) {
            log.error("新增经期失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 编辑经期周期记录
     *
     * @param id  经期记录 ID
     * @param dto 经期周期信息
     * @return 更新后的经期周期信息
     */
    @PutMapping("/{id}")
    public Result<MenstruationCycleVO> updateMenstruation(@PathVariable Long id, @RequestBody MenstruationUpdateDTO dto) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.updateMenstruation(userId, id, dto));
        } catch (Exception e) {
            log.error("编辑经期失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 删除经期记录
     *
     * @param id 经期记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteMenstruation(@PathVariable Long id) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            menstruationCycleService.deleteMenstruation(userId, id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("删除经期失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询最近一次经期记录
     *
     * @return 最近一次经期周期信息
     */
    @GetMapping("/latest")
    public Result<MenstruationCycleVO> getLatestCycle() {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.getLatestCycle(userId));
        } catch (Exception e) {
            log.error("查询最近一次经期失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 获取用户经期统计数据
     *
     * @return 平均周期、平均持续天数、最近统计等数据
     */
    @GetMapping("/stats")
    public Result<MenstruationStatsVO> getCycleStats() {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.getCycleStats(userId));
        } catch (Exception e) {
            log.error("查询经期统计失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 获取指定年月的月视图经期数据
     *
     * @param year  年份
     * @param month 月份
     * @return 对应月份的经期日期、状态等月视图信息
     */
    @GetMapping("/month")
    public Result<MenstruationMonthVO> getMonthView(@RequestParam int year, @RequestParam int month) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.getMonthView(userId, year, month));
        } catch (Exception e) {
            log.error("查询月历经期视图失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 预测下次经期时间
     *
     * @return 预测的下次开始、结束时间，排卵期等信息
     */
    @GetMapping("/prediction")
    public Result<MenstruationPredictionVO> getPrediction() {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        try {
            return Result.ok(menstruationCycleService.getPrediction(userId));
        } catch (Exception e) {
            log.error("查询预测经期失败", e);
            return Result.fail(e.getMessage());
        }
    }
}
