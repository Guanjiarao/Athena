package athena.ground.biz.rpc;

import athena.athenaframework.result.Result;
import constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "athena-insight", contextId = "groundInsightFeignApi")
public interface InsightFeignApi {

    @PostMapping("/athena/insight/internal/note-feature/refresh")
    Result<?> refreshNoteFeature(@RequestBody Map<String, Object> request);
}
