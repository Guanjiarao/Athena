package athena.insight.api;

import athena.athenaframework.result.Result;
import athena.insight.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface InsightFeignApi {

    String PREFIX = "/athena/insight";

    @DeleteMapping(PREFIX + "/note-feature")
    Result<?> deleteNoteFeature(@RequestParam("noteId") Long noteId);

    @PostMapping(PREFIX + "/internal/note-feature/refresh")
    Result<?> refreshNoteFeature(@RequestBody Map<String, Object> request);
}
