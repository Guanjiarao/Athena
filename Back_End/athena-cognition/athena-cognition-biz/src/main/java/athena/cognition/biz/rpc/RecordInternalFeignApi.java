package athena.cognition.biz.rpc;

import athena.athenaframework.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for the existing athena-record internal read chain (contract
 * section 4.8), mirroring athena-insight's InsightRecordFeignApi against
 * RecordInsightController# getRecordsByUserIdAndRange.
 */
@FeignClient(name = "athena-record", contextId = "cognitionRecordFeignApi")
public interface RecordInternalFeignApi {

    @GetMapping("/athena/record/internal/insight/records")
    Result<List<DailyRecordSnapshot>> getRecords(@RequestParam("userId") Long userId,
                                                 @RequestParam("startDate") String startDate,
                                                 @RequestParam("endDate") String endDate);
}
