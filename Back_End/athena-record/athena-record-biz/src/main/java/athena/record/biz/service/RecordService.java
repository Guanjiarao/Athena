package athena.record.biz.service;

import athena.record.biz.domain.dataobject.DailyRecord;
import athena.record.biz.domain.dto.CreateDailyRecordDTO;
import athena.record.biz.domain.dto.UpdateDailyRecordDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public interface RecordService {

    List<LocalDate> getMonthlyMarks(Long userId, int year, int month);

    List<DailyRecord> getDailyDetails(Long userId, LocalDate date);

    List<DailyRecord> getDailyDetailsInRange(Long userId, LocalDate startDate, LocalDate endDate);

    DailyRecord createRecord(Long userId, CreateDailyRecordDTO dto);

    DailyRecord updateRecord(Long userId, Long id, UpdateDailyRecordDTO dto);

    void deleteRecord(Long userId, Long id);
}
