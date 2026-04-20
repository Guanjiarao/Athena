package athena.record.biz.service;

import athena.record.biz.domain.dataobject.MenstruationCycle;
import athena.record.biz.domain.dto.MenstruationEndDTO;
import athena.record.biz.domain.dto.MenstruationStartDTO;
import athena.record.biz.domain.mapper.MenstruationCycleMapper;
import athena.record.biz.domain.vo.MenstruationCycleVO;
import athena.record.biz.domain.vo.MenstruationMonthVO;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import athena.record.biz.domain.vo.MenstruationStatsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenstruationCycleServiceImplTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private MenstruationCycleMapper menstruationCycleMapper;

    @InjectMocks
    private MenstruationCycleServiceImpl menstruationCycleService;

    @Test
    void startMenstruation_shouldCreateActualCycleAndPrediction() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));

        MenstruationCycle previousCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, 28, 0);
        MenstruationCycle predictedCycle = buildCycle(3L, LocalDate.of(2026, 4, 7), LocalDate.of(2026, 4, 11), 5, 28, 1);

        when(menstruationCycleMapper.deletePredictedCycles(USER_ID)).thenReturn(1);
        when(menstruationCycleMapper.selectLatestOpenActualCycle(USER_ID)).thenReturn(null);
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(previousCycle, buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0));
        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(cycle.getIsPredicted() == 0 ? 2L : 3L);
            }
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));
        when(menstruationCycleMapper.selectById(2L)).thenReturn(buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0));
        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Collections.singletonList(buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0)),
                Arrays.asList(
                        buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0),
                        previousCycle
                )
        );

        MenstruationCycleVO result = menstruationCycleService.startMenstruation(USER_ID, dto);

        assertNotNull(result);
        assertEquals(LocalDate.of(2026, 3, 10), result.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 14), result.getDisplayEndDate());
        assertEquals(5, result.getDisplayDurationDays());
        assertEquals(28, result.getCycleLength());

        ArgumentCaptor<MenstruationCycle> captor = ArgumentCaptor.forClass(MenstruationCycle.class);
        verify(menstruationCycleMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<MenstruationCycle> insertedCycles = captor.getAllValues();

        MenstruationCycle insertedActualCycle = insertedCycles.get(0);
        assertEquals(USER_ID, insertedActualCycle.getUserId());
        assertEquals(LocalDate.of(2026, 3, 10), insertedActualCycle.getStartDate());
        assertEquals(28, insertedActualCycle.getCycleLength());
        assertEquals(0, insertedActualCycle.getIsPredicted());

        MenstruationCycle insertedPredictedCycle = insertedCycles.get(1);
        assertEquals(LocalDate.of(2026, 4, 7), insertedPredictedCycle.getStartDate());
        assertEquals(LocalDate.of(2026, 4, 11), insertedPredictedCycle.getEndDate());
        assertEquals(1, insertedPredictedCycle.getIsPredicted());
    }

    @Test
    void startMenstruation_shouldThrowWhenOpenCycleExists() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));

        when(menstruationCycleMapper.selectLatestOpenActualCycle(USER_ID)).thenReturn(buildCycle(2L, LocalDate.of(2026, 3, 5), null, null, null, 0));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> menstruationCycleService.startMenstruation(USER_ID, dto));

        assertEquals("当前存在未结束的经期，不能重复开始", exception.getMessage());
        verify(menstruationCycleMapper, never()).insert(any(MenstruationCycle.class));
    }

    @Test
    void endMenstruation_shouldUpdateLatestCycleAndRefreshPrediction() {
        MenstruationEndDTO dto = new MenstruationEndDTO();
        dto.setEndDate(LocalDate.of(2026, 3, 15));

        MenstruationCycle latestCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0);
        MenstruationCycle olderCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, 28, 0);

        when(menstruationCycleMapper.deletePredictedCycles(USER_ID)).thenReturn(1);
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(latestCycle, latestCycle);
        when(menstruationCycleMapper.selectById(2L)).thenReturn(buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), 6, 28, 0));
        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(latestCycle, olderCycle),
                Collections.singletonList(olderCycle)
        );
        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(10L);
            }
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));

        MenstruationCycleVO result = menstruationCycleService.endMenstruation(USER_ID, dto);

        assertNotNull(result);
        assertEquals(LocalDate.of(2026, 3, 15), result.getEndDate());
        assertEquals(6, result.getDurationDays());

        ArgumentCaptor<MenstruationCycle> updateCaptor = ArgumentCaptor.forClass(MenstruationCycle.class);
        verify(menstruationCycleMapper).updateById(updateCaptor.capture());
        MenstruationCycle updatedCycle = updateCaptor.getValue();
        assertEquals(LocalDate.of(2026, 3, 15), updatedCycle.getEndDate());
        assertEquals(6, updatedCycle.getDurationDays());

        ArgumentCaptor<MenstruationCycle> insertCaptor = ArgumentCaptor.forClass(MenstruationCycle.class);
        verify(menstruationCycleMapper).insert(insertCaptor.capture());
        MenstruationCycle insertedPredictedCycle = insertCaptor.getValue();
        assertEquals(LocalDate.of(2026, 4, 7), insertedPredictedCycle.getStartDate());
        assertEquals(LocalDate.of(2026, 4, 11), insertedPredictedCycle.getEndDate());
        assertEquals(5, insertedPredictedCycle.getDurationDays());
    }

    @Test
    void getMonthView_shouldReturnActualAndPredictedCalendarData() {
        MenstruationCycle actualCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0);
        MenstruationCycle oldActualCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, 28, 0);
        MenstruationCycle predictedCycle = buildCycle(3L, LocalDate.of(2026, 3, 28), LocalDate.of(2026, 4, 1), 5, 28, 1);

        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Collections.singletonList(actualCycle),
                Collections.singletonList(predictedCycle),
                Collections.singletonList(actualCycle),
                Collections.singletonList(oldActualCycle)
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(actualCycle);

        MenstruationMonthVO result = menstruationCycleService.getMonthView(USER_ID, 2026, 3);

        assertEquals(2026, result.getYear());
        assertEquals(3, result.getMonth());
        assertEquals(1, result.getActualCycleList().size());
        assertEquals(1, result.getPredictedCycleList().size());
        assertEquals(LocalDate.of(2026, 3, 10), result.getActualCycleList().get(0).getMonthStartDate());
        assertEquals(LocalDate.of(2026, 3, 14), result.getActualCycleList().get(0).getMonthEndDate());
        assertTrue(result.getActualDates().contains(LocalDate.of(2026, 3, 12)));
        assertTrue(result.getPredictedDates().contains(LocalDate.of(2026, 3, 30)));
        assertEquals(LocalDate.of(2026, 4, 7), result.getNextPredictedStartDate());
        assertEquals(LocalDate.of(2026, 4, 11), result.getNextPredictedEndDate());
    }

    @Test
    void getPrediction_shouldFallbackToDefaultDurationWhenDurationSamplesMissing() {
        MenstruationCycle latestCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0);
        MenstruationCycle previousCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), null, null, 30, 0);

        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(latestCycle, previousCycle),
                Collections.emptyList()
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(latestCycle);

        MenstruationPredictionVO result = menstruationCycleService.getPrediction(USER_ID);

        assertTrue(result.getPredictable());
        assertEquals(29, result.getAverageCycleLength());
        assertEquals(5, result.getAverageDurationDays());
        assertEquals(LocalDate.of(2026, 4, 8), result.getPredictedStartDate());
        assertEquals(LocalDate.of(2026, 4, 12), result.getPredictedEndDate());
    }

    @Test
    void getCycleStats_shouldReturnAveragesAndPrediction() {
        MenstruationCycle latestCycle = buildCycle(3L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), 6, 29, 0);
        MenstruationCycle cycle2 = buildCycle(2L, LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 13), 5, 28, 0);
        MenstruationCycle cycle1 = buildCycle(1L, LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 16), 5, 30, 0);

        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(latestCycle, cycle2, cycle1),
                Arrays.asList(latestCycle, cycle2, cycle1),
                Arrays.asList(latestCycle, cycle2, cycle1),
                Arrays.asList(latestCycle, cycle2, cycle1)
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(latestCycle);

        MenstruationStatsVO result = menstruationCycleService.getCycleStats(USER_ID);

        assertEquals(29, result.getAverageCycleLength());
        assertEquals(5, result.getAverageDurationDays());
        assertEquals(3, result.getCycleSampleCount());
        assertEquals(3, result.getDurationSampleCount());
        assertEquals(LocalDate.of(2026, 4, 8), result.getPredictedNextStartDate());
        assertEquals(LocalDate.of(2026, 4, 12), result.getPredictedNextEndDate());
    }

    @Test
    void findCycleByDate_shouldMatchOpenCycleByDefaultFiveDays() {
        MenstruationCycle openCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), null, null, 28, 0);
        MenstruationCycle oldCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, 28, 0);

        when(menstruationCycleMapper.selectList(any())).thenReturn(Arrays.asList(openCycle, oldCycle));

        MenstruationCycle result = menstruationCycleService.findCycleByDate(USER_ID, LocalDate.of(2026, 3, 13));
        MenstruationCycle notMatched = menstruationCycleService.findCycleByDate(USER_ID, LocalDate.of(2026, 3, 20));

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertNull(notMatched);
    }

    @Test
    void getPrediction_shouldReturnNotPredictableWhenNoCycleLengthSamples() {
        when(menstruationCycleMapper.selectList(any())).thenReturn(Collections.emptyList());

        MenstruationPredictionVO result = menstruationCycleService.getPrediction(USER_ID);

        assertFalse(result.getPredictable());
        assertEquals("周期数据不足，暂时无法预测下次经期", result.getReason());
        assertEquals(5, result.getAverageDurationDays());
    }

    private MenstruationCycle buildCycle(Long id,
                                         LocalDate startDate,
                                         LocalDate endDate,
                                         Integer durationDays,
                                         Integer cycleLength,
                                         Integer isPredicted) {
        MenstruationCycle cycle = new MenstruationCycle();
        cycle.setId(id);
        cycle.setUserId(USER_ID);
        cycle.setStartDate(startDate);
        cycle.setEndDate(endDate);
        cycle.setDurationDays(durationDays);
        cycle.setCycleLength(cycleLength);
        cycle.setIsPredicted(isPredicted);
        return cycle;
    }
}
