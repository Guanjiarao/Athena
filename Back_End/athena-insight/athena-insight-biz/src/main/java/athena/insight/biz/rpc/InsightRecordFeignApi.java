package athena.insight.biz.rpc;

import athena.athenaframework.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "athena-record", contextId = "insightRecordFeignApi")
public interface InsightRecordFeignApi {

    String PREFIX = "/athena/record/internal/insight";

    @GetMapping(PREFIX + "/cycle-stats")
    Result<?> getCycleStats(@RequestParam("userId") Long userId);

    @GetMapping(PREFIX + "/prediction")
    Result<?> getPrediction(@RequestParam("userId") Long userId);

    @GetMapping(PREFIX + "/records")
    Result<?> getRecords(@RequestParam("userId") Long userId,
                         @RequestParam("startDate") String startDate,
                         @RequestParam("endDate") String endDate);
}
