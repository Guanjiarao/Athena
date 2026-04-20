package athena.insight.biz.rpc;

import athena.athenaframework.result.Result;
import constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.SERVICE_NAME, contextId = "insightGroundFeignApi")
public interface InsightGroundFeignApi {

    String PREFIX = "/athena/blog";

    @GetMapping(PREFIX + "/list")
    Result<?> getBlogListPage(@RequestParam("pageNum") Integer pageNum,
                              @RequestParam("pageSize") Integer pageSize);

    @GetMapping(PREFIX + "/Detail")
    Result<?> getBlogDetail(@RequestParam("blog_id") Long blogId,
                            @RequestParam("type") Byte type);

    @GetMapping(PREFIX + "/myList")
    Result<?> getBlogListByUserId(@RequestParam(value = "userId", required = false) Long userId,
                                  @RequestParam("pageNum") Integer pageNum,
                                  @RequestParam("pageSize") Integer pageSize);

    @GetMapping(PREFIX + "/listByTypeId")
    Result<?> getBlogListByType(@RequestParam("type") Integer type,
                                @RequestParam("pageNum") Integer pageNum,
                                @RequestParam("pageSize") Integer pageSize);

    @GetMapping(PREFIX + "/likeList")
    Result<?> likeList();

    @GetMapping(PREFIX + "/collectList")
    Result<?> collectList();

    @GetMapping(PREFIX + "/viewHistory")
    Result<?> viewHistory(@RequestParam(value = "cursor", required = false) Long cursor,
                          @RequestParam("pageSize") Integer pageSize);
}
