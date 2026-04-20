package athena.ground.api;

import athena.athenaframework.result.Result;

import constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface GroundFeignApi {
    String PREFIX = "/athena/blog";

    @PostMapping(PREFIX+"/commentadd")
    Result<?> commentAdd(@RequestParam Long noteId, @RequestParam Long num);
}
