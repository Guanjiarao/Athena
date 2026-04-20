package athena.record.biz.service;

import athena.record.biz.domain.dataobject.MenstruationCycle;
import athena.record.biz.domain.dto.MenstruationEndDTO;
import athena.record.biz.domain.dto.MenstruationStartDTO;
import athena.record.biz.domain.mapper.MenstruationCycleMapper;
import athena.record.biz.domain.vo.MenstruationCycleVO;
import athena.record.biz.domain.vo.MenstruationMonthVO;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import athena.record.biz.domain.vo.MenstruationStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenstruationCycleServiceImpl implements MenstruationCycleService {

    private static final int ACTUAL_DATA_FLAG = 0;
    private static final int PREDICTED_DATA_FLAG = 1;
    private static final int DEFAULT_DURATION_DAYS = 5;
    private static final int PREDICTION_SAMPLE_SIZE = 3;

    private final MenstruationCycleMapper menstruationCycleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenstruationCycleVO startMenstruation(Long userId, MenstruationStartDTO dto) {
        LocalDate startDate = requireDate(dto == null ? null : dto.getStartDate(), "开始日期不能为空");
        validateUserId(userId);

        MenstruationCycle openCycle = menstruationCycleMapper.selectLatestOpenActualCycle(userId);
        if (openCycle != null) {
            throw new IllegalStateException("当前存在未结束的经期，不能重复开始");
        }

        MenstruationCycle latestActualCycle = menstruationCycleMapper.selectLatestActualCycle(userId);
        if (latestActualCycle != null && !startDate.isAfter(latestActualCycle.getStartDate())) {
            throw new IllegalArgumentException("开始日期必须晚于最近一次经期开始日期");
        }

        MenstruationCycle cycle = new MenstruationCycle();
        cycle.setUserId(userId);
        cycle.setStartDate(startDate);
        cycle.setIsPredicted(ACTUAL_DATA_FLAG);
        if (latestActualCycle != null) {
            cycle.setCycleLength((int) ChronoUnit.DAYS.between(latestActualCycle.getStartDate(), startDate));
        }
        menstruationCycleMapper.insert(cycle);

        refreshPredictedCycle(userId);
        return toCycleVO(menstruationCycleMapper.selectById(cycle.getId()), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenstruationCycleVO endMenstruation(Long userId, MenstruationEndDTO dto) {
        LocalDate endDate = requireDate(dto == null ? null : dto.getEndDate(), "结束日期不能为空");
        validateUserId(userId);

        MenstruationCycle latestActualCycle = menstruationCycleMapper.selectLatestActualCycle(userId);
        if (latestActualCycle == null) {
            throw new IllegalStateException("当前没有可结束的经期记录");
        }
        if (endDate.isBefore(latestActualCycle.getStartDate())) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }

        // 结束接口允许重复调用，始终以最新一次提交的结束日期为准进行修正。
        latestActualCycle.setEndDate(endDate);
        latestActualCycle.setDurationDays(calculateDurationDays(latestActualCycle.getStartDate(), endDate));
        menstruationCycleMapper.updateById(latestActualCycle);

        refreshPredictedCycle(userId);
        return toCycleVO(menstruationCycleMapper.selectById(latestActualCycle.getId()), null, null);
    }

    @Override
    public MenstruationCycleVO getLatestCycle(Long userId) {
        validateUserId(userId);
        MenstruationCycle latestActualCycle = menstruationCycleMapper.selectLatestActualCycle(userId);
        if (latestActualCycle == null) {
            return null;
        }
        return toCycleVO(latestActualCycle, null, null);
    }

    @Override
    public MenstruationStatsVO getCycleStats(Long userId) {
        validateUserId(userId);

        List<MenstruationCycle> cycleLengthSamples = listActualCycles(userId, MenstruationCycle::getCycleLength, null);
        List<MenstruationCycle> durationSamples = listActualCycles(userId, MenstruationCycle::getDurationDays, null);
        MenstruationPredictionVO predictionVO = buildPrediction(userId);

        return MenstruationStatsVO.builder()
                .averageCycleLength(calculateAverage(cycleLengthSamples.stream().map(MenstruationCycle::getCycleLength).collect(Collectors.toList())))
                .averageDurationDays(calculateAverage(durationSamples.stream().map(MenstruationCycle::getDurationDays).collect(Collectors.toList())))
                .cycleSampleCount(cycleLengthSamples.size())
                .durationSampleCount(durationSamples.size())
                .predictedNextStartDate(predictionVO.getPredictedStartDate())
                .predictedNextEndDate(predictionVO.getPredictedEndDate())
                .build();
    }

    @Override
    public MenstruationMonthVO getMonthView(Long userId, int year, int month) {
        validateUserId(userId);
        validateYearMonth(year, month);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();

        List<MenstruationCycleVO> actualCycleList = listCyclesForMonth(userId, ACTUAL_DATA_FLAG, monthStart, monthEnd);
        List<MenstruationCycleVO> predictedCycleList = listCyclesForMonth(userId, PREDICTED_DATA_FLAG, monthStart, monthEnd);
        List<LocalDate> actualDates = expandMonthDates(actualCycleList);
        List<LocalDate> predictedDates = expandMonthDates(predictedCycleList);
        MenstruationPredictionVO predictionVO = buildPrediction(userId);
        LocalDate today = LocalDate.now();

        return MenstruationMonthVO.builder()
                .year(year)
                .month(month)
                .actualCycleList(actualCycleList)
                .predictedCycleList(predictedCycleList)
                .actualDates(actualDates)
                .predictedDates(predictedDates)
                .todayInActualCycle(actualDates.contains(today))
                .todayInPredictedCycle(predictedDates.contains(today))
                .nextPredictedStartDate(predictionVO.getPredictedStartDate())
                .nextPredictedEndDate(predictionVO.getPredictedEndDate())
                .build();
    }

    @Override
    public MenstruationPredictionVO getPrediction(Long userId) {
        validateUserId(userId);
        return buildPrediction(userId);
    }

    @Override
    public MenstruationCycle findCycleByDate(Long userId, LocalDate recordDate) {
        validateUserId(userId);
        requireDate(recordDate, "记录日期不能为空");

        LambdaQueryWrapper<MenstruationCycle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MenstruationCycle::getUserId, userId)
                .eq(MenstruationCycle::getIsPredicted, ACTUAL_DATA_FLAG)
                .le(MenstruationCycle::getStartDate, recordDate)
                .orderByDesc(MenstruationCycle::getStartDate);

        List<MenstruationCycle> cycleList = menstruationCycleMapper.selectList(queryWrapper);
        for (MenstruationCycle cycle : cycleList) {
            LocalDate displayEndDate = calculateDisplayEndDate(cycle);
            if (!recordDate.isBefore(cycle.getStartDate()) && !recordDate.isAfter(displayEndDate)) {
                return cycle;
            }
        }
        return null;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new IllegalArgumentException(message);
        }
        return date;
    }

    private void validateYearMonth(int year, int month) {
        try {
            YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("年月参数不合法");
        }
    }

    private List<MenstruationCycleVO> listCyclesForMonth(Long userId, int dataFlag, LocalDate monthStart, LocalDate monthEnd) {
        LambdaQueryWrapper<MenstruationCycle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MenstruationCycle::getUserId, userId)
                .eq(MenstruationCycle::getIsPredicted, dataFlag)
                .le(MenstruationCycle::getStartDate, monthEnd)
                .orderByAsc(MenstruationCycle::getStartDate);

        return menstruationCycleMapper.selectList(queryWrapper).stream()
                .map(cycle -> toCycleVO(cycle, monthStart, monthEnd))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private MenstruationCycleVO toCycleVO(MenstruationCycle cycle, LocalDate monthStart, LocalDate monthEnd) {
        if (cycle == null) {
            return null;
        }

        LocalDate displayEndDate = calculateDisplayEndDate(cycle);
        LocalDate rangeStart = null;
        LocalDate rangeEnd = null;
        if (monthStart != null && monthEnd != null) {
            rangeStart = cycle.getStartDate().isBefore(monthStart) ? monthStart : cycle.getStartDate();
            rangeEnd = displayEndDate.isAfter(monthEnd) ? monthEnd : displayEndDate;
            if (rangeStart.isAfter(rangeEnd)) {
                return null;
            }
        }

        return MenstruationCycleVO.builder()
                .id(cycle.getId())
                .startDate(cycle.getStartDate())
                .endDate(cycle.getEndDate())
                .displayEndDate(displayEndDate)
                .durationDays(cycle.getDurationDays())
                .displayDurationDays(calculateDisplayDurationDays(cycle))
                .cycleLength(cycle.getCycleLength())
                .predicted(PREDICTED_DATA_FLAG == cycle.getIsPredicted())
                .monthStartDate(rangeStart)
                .monthEndDate(rangeEnd)
                .build();
    }

    private List<LocalDate> expandMonthDates(List<MenstruationCycleVO> cycleList) {
        if (cycleList == null || cycleList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<LocalDate> dateSet = new LinkedHashSet<>();
        for (MenstruationCycleVO cycleVO : cycleList) {
            LocalDate current = cycleVO.getMonthStartDate();
            while (current != null && cycleVO.getMonthEndDate() != null && !current.isAfter(cycleVO.getMonthEndDate())) {
                dateSet.add(current);
                current = current.plusDays(1);
            }
        }
        return new ArrayList<>(dateSet);
    }

    private LocalDate calculateDisplayEndDate(MenstruationCycle cycle) {
        if (cycle.getEndDate() != null) {
            return cycle.getEndDate();
        }
        return cycle.getStartDate().plusDays(DEFAULT_DURATION_DAYS - 1L);
    }

    private Integer calculateDisplayDurationDays(MenstruationCycle cycle) {
        if (cycle.getDurationDays() != null) {
            return cycle.getDurationDays();
        }
        return calculateDurationDays(cycle.getStartDate(), calculateDisplayEndDate(cycle));
    }

    private int calculateDurationDays(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private MenstruationPredictionVO buildPrediction(Long userId) {
        List<MenstruationCycle> cycleSamples = listActualCycles(userId, MenstruationCycle::getCycleLength, PREDICTION_SAMPLE_SIZE);
        if (cycleSamples.isEmpty()) {
            return MenstruationPredictionVO.builder()
                    .predictable(Boolean.FALSE)
                    .reason("周期数据不足，暂时无法预测下次经期")
                    .referenceCycleCount(0)
                    .durationSampleCount(0)
                    .averageDurationDays(DEFAULT_DURATION_DAYS)
                    .build();
        }

        MenstruationCycle latestActualCycle = menstruationCycleMapper.selectLatestActualCycle(userId);
        if (latestActualCycle == null) {
            return MenstruationPredictionVO.builder()
                    .predictable(Boolean.FALSE)
                    .reason("暂无实际经期数据")
                    .referenceCycleCount(0)
                    .durationSampleCount(0)
                    .averageDurationDays(DEFAULT_DURATION_DAYS)
                    .build();
        }

        List<MenstruationCycle> durationSamples = listActualCycles(userId, MenstruationCycle::getDurationDays, PREDICTION_SAMPLE_SIZE);
        Integer averageCycleLength = calculateAverage(cycleSamples.stream().map(MenstruationCycle::getCycleLength).collect(Collectors.toList()));
        Integer averageDurationDays = calculateAverage(durationSamples.stream().map(MenstruationCycle::getDurationDays).collect(Collectors.toList()));
        if (averageDurationDays == null) {
            averageDurationDays = DEFAULT_DURATION_DAYS;
        }

        LocalDate predictedStartDate = latestActualCycle.getStartDate().plusDays(averageCycleLength);
        LocalDate predictedEndDate = predictedStartDate.plusDays(averageDurationDays - 1L);

        return MenstruationPredictionVO.builder()
                .predictable(Boolean.TRUE)
                .referenceCycleCount(cycleSamples.size())
                .durationSampleCount(durationSamples.size())
                .averageCycleLength(averageCycleLength)
                .averageDurationDays(averageDurationDays)
                .predictedStartDate(predictedStartDate)
                .predictedEndDate(predictedEndDate)
                .build();
    }

    private void refreshPredictedCycle(Long userId) {
        menstruationCycleMapper.deletePredictedCycles(userId);
        MenstruationPredictionVO predictionVO = buildPrediction(userId);
        if (!Boolean.TRUE.equals(predictionVO.getPredictable())) {
            return;
        }

        MenstruationCycle predictedCycle = new MenstruationCycle();
        predictedCycle.setUserId(userId);
        predictedCycle.setStartDate(predictionVO.getPredictedStartDate());
        predictedCycle.setEndDate(predictionVO.getPredictedEndDate());
        predictedCycle.setDurationDays(predictionVO.getAverageDurationDays());
        predictedCycle.setCycleLength(predictionVO.getAverageCycleLength());
        predictedCycle.setIsPredicted(PREDICTED_DATA_FLAG);
        menstruationCycleMapper.insert(predictedCycle);
    }

    private List<MenstruationCycle> listActualCycles(Long userId,
                                                     java.util.function.Function<MenstruationCycle, Integer> valueGetter,
                                                     Integer limit) {
        LambdaQueryWrapper<MenstruationCycle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MenstruationCycle::getUserId, userId)
                .eq(MenstruationCycle::getIsPredicted, ACTUAL_DATA_FLAG)
                .orderByDesc(MenstruationCycle::getStartDate);
        if (limit != null) {
            queryWrapper.last("LIMIT " + limit);
        }

        return menstruationCycleMapper.selectList(queryWrapper).stream()
                .filter(cycle -> valueGetter.apply(cycle) != null)
                .collect(Collectors.toList());
    }

    private Integer calculateAverage(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        return Math.round((float) sum / values.size());
    }
}
