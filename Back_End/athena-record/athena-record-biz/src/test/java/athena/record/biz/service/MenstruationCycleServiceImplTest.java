package athena.record.biz.service;

import athena.record.biz.domain.dataobject.MenstruationCycle;
import athena.record.biz.domain.dto.MenstruationStartDTO;
import athena.record.biz.domain.dto.MenstruationUpdateDTO;
import athena.record.biz.domain.mapper.MenstruationCycleMapper;
import athena.record.biz.domain.vo.MenstruationCycleVO;
import athena.record.biz.domain.vo.MenstruationMonthVO;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

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
    void startMenstruation_shouldCreateActualCycleWithDefaultEndDateAndRefreshPrediction() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));

        MenstruationCycle previousCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, null, 0);
        MenstruationCycle insertedCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);

        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(cycle.getIsPredicted() == 0 ? 2L : 3L);
            }
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));
        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(previousCycle, insertedCycle),
                Collections.singletonList(insertedCycle),
                Collections.singletonList(insertedCycle)
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(insertedCycle);
        when(menstruationCycleMapper.selectById(2L)).thenReturn(insertedCycle);

        MenstruationCycleVO result = menstruationCycleService.startMenstruation(USER_ID, dto);

        assertNotNull(result);
        assertEquals(LocalDate.of(2026, 3, 10), result.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 14), result.getEndDate());
        assertEquals(5, result.getDurationDays());
        assertEquals(28, result.getCycleLength());

        ArgumentCaptor<MenstruationCycle> insertCaptor = ArgumentCaptor.forClass(MenstruationCycle.class);
        verify(menstruationCycleMapper, org.mockito.Mockito.times(2)).insert(insertCaptor.capture());
        MenstruationCycle insertedActualCycle = insertCaptor.getAllValues().get(0);
        assertEquals(USER_ID, insertedActualCycle.getUserId());
        assertEquals(LocalDate.of(2026, 3, 10), insertedActualCycle.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 14), insertedActualCycle.getEndDate());
        assertEquals(5, insertedActualCycle.getDurationDays());
        assertEquals(0, insertedActualCycle.getIsPredicted());
    }

    @Test
    void startMenstruation_shouldUseSubmittedEndDate() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));
        dto.setEndDate(LocalDate.of(2026, 3, 16));

        MenstruationCycle insertedCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 16), 7, null, 0);
        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            cycle.setId(2L);
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));
        when(menstruationCycleMapper.selectList(any())).thenReturn(Collections.singletonList(insertedCycle), Collections.emptyList());
        when(menstruationCycleMapper.selectById(2L)).thenReturn(insertedCycle);

        menstruationCycleService.startMenstruation(USER_ID, dto);

        ArgumentCaptor<MenstruationCycle> insertCaptor = ArgumentCaptor.forClass(MenstruationCycle.class);
        verify(menstruationCycleMapper).insert(insertCaptor.capture());
        assertEquals(LocalDate.of(2026, 3, 16), insertCaptor.getValue().getEndDate());
        assertEquals(7, insertCaptor.getValue().getDurationDays());
    }

    @Test
    void startMenstruation_shouldThrowWhenEndDateBeforeStartDate() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));
        dto.setEndDate(LocalDate.of(2026, 3, 9));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menstruationCycleService.startMenstruation(USER_ID, dto));

        assertEquals("结束日期不能早于开始日期", exception.getMessage());
        verify(menstruationCycleMapper, never()).insert(any(MenstruationCycle.class));
    }

    @Test
    void startMenstruation_shouldThrowWhenDateRangeOverlapsExistingCycle() {
        MenstruationStartDTO dto = new MenstruationStartDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 12));
        dto.setEndDate(LocalDate.of(2026, 3, 16));

        when(menstruationCycleMapper.selectCount(any())).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menstruationCycleService.startMenstruation(USER_ID, dto));

        assertEquals("经期日期不能与已有记录重叠", exception.getMessage());
        verify(menstruationCycleMapper, never()).insert(any(MenstruationCycle.class));
    }

    @Test
    void updateMenstruation_shouldUpdateCycleAndRecalculateCycleLengths() {
        MenstruationUpdateDTO dto = new MenstruationUpdateDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 12));
        dto.setEndDate(LocalDate.of(2026, 3, 17));

        MenstruationCycle targetCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);
        MenstruationCycle previousCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, null, 0);
        MenstruationCycle updatedCycle = buildCycle(2L, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 17), 6, 30, 0);

        when(menstruationCycleMapper.selectById(2L)).thenReturn(targetCycle, updatedCycle);
        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(previousCycle, targetCycle),
                Collections.singletonList(targetCycle),
                Collections.singletonList(targetCycle)
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(updatedCycle);
        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(10L);
            }
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));

        MenstruationCycleVO result = menstruationCycleService.updateMenstruation(USER_ID, 2L, dto);

        assertEquals(LocalDate.of(2026, 3, 12), result.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 17), result.getEndDate());
        assertEquals(6, result.getDurationDays());
    }

    @Test
    void updateMenstruation_shouldThrowWhenCycleBelongsToAnotherUser() {
        MenstruationUpdateDTO dto = new MenstruationUpdateDTO();
        dto.setStartDate(LocalDate.of(2026, 3, 10));

        MenstruationCycle targetCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);
        targetCycle.setUserId(2002L);
        when(menstruationCycleMapper.selectById(2L)).thenReturn(targetCycle);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> menstruationCycleService.updateMenstruation(USER_ID, 2L, dto));

        assertEquals("无权操作该经期记录", exception.getMessage());
        verify(menstruationCycleMapper, never()).updateById(any(MenstruationCycle.class));
    }

    @Test
    void deleteMenstruation_shouldDeleteActualCycleAndRecalculatePrediction() {
        MenstruationCycle targetCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);
        MenstruationCycle latestCycle = buildCycle(3L, LocalDate.of(2026, 4, 7), LocalDate.of(2026, 4, 11), 5, 28, 0);
        MenstruationCycle olderCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, null, 0);

        when(menstruationCycleMapper.selectById(2L)).thenReturn(targetCycle);
        when(menstruationCycleMapper.deletePredictedCycles(USER_ID)).thenReturn(1);
        when(menstruationCycleMapper.selectList(any())).thenReturn(
                Arrays.asList(olderCycle, latestCycle),
                Arrays.asList(latestCycle, olderCycle),
                Arrays.asList(latestCycle, olderCycle)
        );
        when(menstruationCycleMapper.selectLatestActualCycle(USER_ID)).thenReturn(latestCycle);
        doAnswer(invocation -> {
            MenstruationCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(10L);
            }
            return 1;
        }).when(menstruationCycleMapper).insert(any(MenstruationCycle.class));

        menstruationCycleService.deleteMenstruation(USER_ID, 2L);

        verify(menstruationCycleMapper).deleteById(2L);
        verify(menstruationCycleMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(MenstruationCycle.class));
    }

    @Test
    void getMonthView_shouldReturnActualAndPredictedCalendarData() {
        MenstruationCycle actualCycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);
        MenstruationCycle oldActualCycle = buildCycle(1L, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 14), 5, null, 0);
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
        assertTrue(result.getActualDates().contains(LocalDate.of(2026, 3, 12)));
        assertTrue(result.getPredictedDates().contains(LocalDate.of(2026, 3, 30)));
    }

    @Test
    void getPrediction_shouldReturnNotPredictableWhenNoCycleLengthSamples() {
        when(menstruationCycleMapper.selectList(any())).thenReturn(Collections.emptyList());

        MenstruationPredictionVO result = menstruationCycleService.getPrediction(USER_ID);

        assertFalse(result.getPredictable());
        assertEquals("周期数据不足，暂时无法预测下次经期", result.getReason());
        assertEquals(5, result.getAverageDurationDays());
    }

    @Test
    void findCycleByDate_shouldMatchCycleByPersistedEndDate() {
        MenstruationCycle cycle = buildCycle(2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14), 5, 28, 0);
        when(menstruationCycleMapper.selectList(any())).thenReturn(Collections.singletonList(cycle));

        MenstruationCycle result = menstruationCycleService.findCycleByDate(USER_ID, LocalDate.of(2026, 3, 13));

        assertNotNull(result);
        assertEquals(2L, result.getId());
    }

    @Test
    void findCycleByDate_shouldReturnNullWhenNoCycleMatches() {
        when(menstruationCycleMapper.selectList(any())).thenReturn(Collections.emptyList());

        MenstruationCycle result = menstruationCycleService.findCycleByDate(USER_ID, LocalDate.of(2026, 3, 20));

        assertNull(result);
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
