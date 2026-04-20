package athena.record.biz.service;

import athena.record.biz.domain.dataobject.DailyRecord;
import athena.record.biz.domain.dto.CreateDailyRecordDTO;
import athena.record.biz.domain.dto.UpdateDailyRecordDTO;
import athena.record.biz.domain.mapper.DailyRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class RecordServiceImpl implements RecordService {

    @Autowired
    private DailyRecordMapper recordMapper;

    /**
     * 获取日历标记：返回某个月哪些天填了数据
     */
    public List<LocalDate> getMonthlyMarks(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
        return recordMapper.getRecordedDatesInMonth(userId, startDate, endDate);
    }

    /**
     * 查询某天所有的记录详情
     */
    public List<DailyRecord> getDailyDetails(Long userId, LocalDate date) {
        LambdaQueryWrapper<DailyRecord> query = new LambdaQueryWrapper<>();
        query.eq(DailyRecord::getUserId, userId)
                .eq(DailyRecord::getRecordDate, date)
                .orderByDesc(DailyRecord::getId);
        return recordMapper.selectList(query);
    }

    @Override
    public List<DailyRecord> getDailyDetailsInRange(Long userId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<DailyRecord> query = new LambdaQueryWrapper<>();
        query.eq(DailyRecord::getUserId, userId)
                .ge(DailyRecord::getRecordDate, startDate)
                .le(DailyRecord::getRecordDate, endDate)
                .orderByDesc(DailyRecord::getRecordDate)
                .orderByDesc(DailyRecord::getId);
        return recordMapper.selectList(query);
    }

    public DailyRecord createRecord(Long userId, CreateDailyRecordDTO dto) {
        validateCreateDto(dto);
        DailyRecord record = new DailyRecord();
        record.setUserId(userId);
        record.setRecordDate(dto.getRecordDate());
        record.setModeType(dto.getModeType());
        record.setRecordItemId(dto.getRecordItemId());
        record.setRecordValue(dto.getRecordValue().trim());
        recordMapper.insert(record);
        return record;
    }

    public DailyRecord updateRecord(Long userId, Long id, UpdateDailyRecordDTO dto) {
        validateUpdateDto(dto);
        DailyRecord existingRecord = getOwnedRecord(userId, id, "无权修改该记录");
        existingRecord.setModeType(dto.getModeType());
        existingRecord.setRecordValue(dto.getRecordValue().trim());
        recordMapper.updateById(existingRecord);
        return existingRecord;
    }

    public void deleteRecord(Long userId, Long id) {
        getOwnedRecord(userId, id, "无权删除该记录");
        recordMapper.deleteById(id);
    }

    private void validateCreateDto(CreateDailyRecordDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("记录内容不能为空");
        }
        if (dto.getRecordDate() == null) {
            throw new IllegalArgumentException("记录日期不能为空");
        }
        if (dto.getModeType() == null) {
            throw new IllegalArgumentException("模式类型不能为空");
        }
        if (dto.getRecordItemId() == null) {
            throw new IllegalArgumentException("记录类型不能为空");
        }
        if (dto.getRecordValue() == null || dto.getRecordValue().trim().isEmpty()) {
            throw new IllegalArgumentException("记录内容不能为空");
        }
    }

    private void validateUpdateDto(UpdateDailyRecordDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("记录内容不能为空");
        }
        if (dto.getModeType() == null) {
            throw new IllegalArgumentException("模式类型不能为空");
        }
        if (dto.getRecordValue() == null || dto.getRecordValue().trim().isEmpty()) {
            throw new IllegalArgumentException("记录内容不能为空");
        }
    }

    private DailyRecord getOwnedRecord(Long userId, Long id, String forbiddenMessage) {
        DailyRecord existingRecord = recordMapper.selectById(id);
        if (existingRecord == null) {
            throw new IllegalStateException("记录不存在");
        }
        if (!userId.equals(existingRecord.getUserId())) {
            throw new IllegalStateException(forbiddenMessage);
        }
        return existingRecord;
    }
}
