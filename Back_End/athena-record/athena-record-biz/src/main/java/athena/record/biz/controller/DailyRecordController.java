package athena.record.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.record.biz.domain.dataobject.DailyRecord;
import athena.record.biz.domain.dto.CreateDailyRecordDTO;
import athena.record.biz.domain.dto.UpdateDailyRecordDTO;
import athena.record.biz.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/athena/record")
public class DailyRecordController {

    @Autowired
    private RecordService recordService;

    @GetMapping("/marks")
    public Result<List<LocalDate>> getMonthlyMarks(@RequestParam int year, @RequestParam int month) {
        Long userId = UserIdHolder.getUserId();
        List<LocalDate> dates = recordService.getMonthlyMarks(userId, year, month);
        return Result.ok(dates);
    }

    @GetMapping("/detail")
    public Result<List<DailyRecord>> getDailyDetail(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Long userId = UserIdHolder.getUserId();
        List<DailyRecord> details = recordService.getDailyDetails(userId, date);
        return Result.ok(details);
    }

    @PostMapping
    public Result<DailyRecord> createRecord(@RequestBody CreateDailyRecordDTO dto) {
        Long userId = UserIdHolder.getUserId();
        DailyRecord record = recordService.createRecord(userId, dto);
        return Result.ok(record);
    }

    @PutMapping("/{id}")
    public Result<DailyRecord> updateRecord(@PathVariable Long id, @RequestBody UpdateDailyRecordDTO dto) {
        Long userId = UserIdHolder.getUserId();
        DailyRecord record = recordService.updateRecord(userId, id, dto);
        return Result.ok(record);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteRecord(@PathVariable Long id) {
        Long userId = UserIdHolder.getUserId();
        recordService.deleteRecord(userId, id);
        return Result.ok("删除成功");
    }
}
