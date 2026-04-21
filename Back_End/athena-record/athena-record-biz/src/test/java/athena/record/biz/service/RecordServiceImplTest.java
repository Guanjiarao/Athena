package athena.record.biz.service;

import athena.record.biz.domain.dataobject.DailyRecord;
import athena.record.biz.domain.dataobject.DictRecordItem;
import athena.record.biz.domain.dto.CreateDailyRecordDTO;
import athena.record.biz.domain.dto.UpdateDailyRecordDTO;
import athena.record.biz.domain.mapper.DailyRecordMapper;
import athena.record.biz.domain.mapper.DictRecordItemMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 2002L;

    @Mock
    private DailyRecordMapper recordMapper;

    @Mock
    private DictRecordItemMapper dictRecordItemMapper;

    @InjectMocks
    private RecordServiceImpl recordService;

    @Test
    void createRecord_shouldInsertMultipleEntriesForSameItemOnSameDate() {
        CreateDailyRecordDTO first = buildCreateDto("上午一次");
        CreateDailyRecordDTO second = buildCreateDto("晚上一次");
        when(dictRecordItemMapper.selectById(101)).thenReturn(buildDictRecordItem(101, 2));

        recordService.createRecord(USER_ID, first);
        recordService.createRecord(USER_ID, second);

        ArgumentCaptor<DailyRecord> captor = ArgumentCaptor.forClass(DailyRecord.class);
        verify(recordMapper, times(2)).insert(captor.capture());
        List<DailyRecord> insertedRecords = captor.getAllValues();

        assertEquals(2, insertedRecords.size());
        assertEquals(USER_ID, insertedRecords.get(0).getUserId());
        assertEquals(LocalDate.of(2026, 3, 11), insertedRecords.get(0).getRecordDate());
        assertEquals(2, insertedRecords.get(0).getModeType());
        assertEquals(101, insertedRecords.get(0).getRecordItemId());
        assertEquals("上午一次", insertedRecords.get(0).getRecordValue());
        assertEquals("晚上一次", insertedRecords.get(1).getRecordValue());
    }

    @Test
    void createRecord_shouldThrowWhenModeTypeIsInvalid() {
        CreateDailyRecordDTO dto = buildCreateDto("晚上一次");
        dto.setModeType(3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> recordService.createRecord(USER_ID, dto));

        assertEquals("模式类型不合法", exception.getMessage());
        verify(recordMapper, never()).insert(any(DailyRecord.class));
        verify(dictRecordItemMapper, never()).selectById(any());
    }

    @Test
    void createRecord_shouldThrowWhenRecordItemDoesNotMatchModeType() {
        CreateDailyRecordDTO dto = buildCreateDto("晚上一次");
        when(dictRecordItemMapper.selectById(101)).thenReturn(buildDictRecordItem(101, 1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> recordService.createRecord(USER_ID, dto));

        assertEquals("记录类型与模式类型不匹配", exception.getMessage());
        verify(recordMapper, never()).insert(any(DailyRecord.class));
    }

    @Test
    void updateRecord_shouldUpdateTargetRecordOnly() {
        DailyRecord existing = buildRecord(11L, USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "上午一次");
        UpdateDailyRecordDTO dto = new UpdateDailyRecordDTO();
        dto.setModeType(2);
        dto.setRecordValue("晚上一次");

        when(recordMapper.selectById(11L)).thenReturn(existing);
        when(dictRecordItemMapper.selectById(101)).thenReturn(buildDictRecordItem(101, 2));

        recordService.updateRecord(USER_ID, 11L, dto);

        ArgumentCaptor<DailyRecord> captor = ArgumentCaptor.forClass(DailyRecord.class);
        verify(recordMapper).updateById(captor.capture());
        DailyRecord updated = captor.getValue();
        assertEquals(11L, updated.getId());
        assertEquals(USER_ID, updated.getUserId());
        assertEquals(2, updated.getModeType());
        assertEquals("晚上一次", updated.getRecordValue());
        assertEquals(101, updated.getRecordItemId());
    }

    @Test
    void updateRecord_shouldThrowWhenRecordBelongsToAnotherUser() {
        DailyRecord existing = buildRecord(11L, OTHER_USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "上午一次");
        UpdateDailyRecordDTO dto = new UpdateDailyRecordDTO();
        dto.setModeType(2);
        dto.setRecordValue("晚上一次");

        when(recordMapper.selectById(11L)).thenReturn(existing);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> recordService.updateRecord(USER_ID, 11L, dto));

        assertEquals("无权修改该记录", exception.getMessage());
        verify(recordMapper, never()).updateById(any(DailyRecord.class));
    }

    @Test
    void deleteRecord_shouldDeleteTargetRecordOnly() {
        DailyRecord existing = buildRecord(11L, USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "上午一次");
        when(recordMapper.selectById(11L)).thenReturn(existing);

        recordService.deleteRecord(USER_ID, 11L);

        verify(recordMapper).deleteById(11L);
    }

    @Test
    void deleteRecord_shouldThrowWhenRecordBelongsToAnotherUser() {
        DailyRecord existing = buildRecord(11L, OTHER_USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "上午一次");
        when(recordMapper.selectById(11L)).thenReturn(existing);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> recordService.deleteRecord(USER_ID, 11L));

        assertEquals("无权删除该记录", exception.getMessage());
        verify(recordMapper, never()).deleteById(11L);
    }

    @Test
    void getDailyDetails_shouldReturnRecordsInDescendingIdOrder() {
        DailyRecord newer = buildRecord(12L, USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "晚上一次");
        DailyRecord older = buildRecord(11L, USER_ID, LocalDate.of(2026, 3, 11), 2, 101, "上午一次");

        when(recordMapper.selectList(any())).thenReturn(Arrays.asList(newer, older));

        List<DailyRecord> result = recordService.getDailyDetails(USER_ID, LocalDate.of(2026, 3, 11));

        assertEquals(2, result.size());
        assertEquals(12L, result.get(0).getId());
        assertEquals(11L, result.get(1).getId());
    }

    @Test
    void getMonthlyMarks_shouldKeepSingleDateEvenWhenMultipleRecordsExist() {
        when(recordMapper.getRecordedDatesInMonth(USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(Collections.singletonList(LocalDate.of(2026, 3, 11)));

        List<LocalDate> result = recordService.getMonthlyMarks(USER_ID, 2026, 3);

        assertEquals(1, result.size());
        assertTrue(result.contains(LocalDate.of(2026, 3, 11)));
    }

    private CreateDailyRecordDTO buildCreateDto(String value) {
        CreateDailyRecordDTO dto = new CreateDailyRecordDTO();
        dto.setRecordDate(LocalDate.of(2026, 3, 11));
        dto.setModeType(2);
        dto.setRecordItemId(101);
        dto.setRecordValue(value);
        return dto;
    }

    private DailyRecord buildRecord(Long id,
                                    Long userId,
                                    LocalDate recordDate,
                                    Integer modeType,
                                    Integer recordItemId,
                                    String recordValue) {
        DailyRecord record = new DailyRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setRecordDate(recordDate);
        record.setModeType(modeType);
        record.setRecordItemId(recordItemId);
        record.setRecordValue(recordValue);
        return record;
    }

    private DictRecordItem buildDictRecordItem(Integer id, Integer modeType) {
        DictRecordItem dictRecordItem = new DictRecordItem();
        dictRecordItem.setId(id);
        dictRecordItem.setModeType(modeType);
        return dictRecordItem;
    }
}
