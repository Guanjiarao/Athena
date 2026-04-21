package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.insight.biz.domain.dto.RecommendQueryDTO;
import athena.insight.biz.service.RecommendationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/athena/insight")
public class InsightRecommendController {

    @Resource
    private RecommendationService recommendationService;

    @GetMapping("/recommend")
    public Result recommend(@RequestParam("type") Byte type,
                            @RequestParam(value = "channelId", required = false) Integer channelId,
                            @RequestParam(defaultValue = "1") Integer pageNum,
                            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserIdHolder.getUserId();
        log.info("[InsightRecommend] 收到推荐请求, userId={}, type={}, channelId={}, pageNum={}, pageSize={}", userId, type, channelId, pageNum, pageSize);

        RecommendQueryDTO request = new RecommendQueryDTO();
        request.setType(type);
        request.setChannelId(channelId);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return Result.ok(recommendationService.recommend(userId, request));
    }
}
