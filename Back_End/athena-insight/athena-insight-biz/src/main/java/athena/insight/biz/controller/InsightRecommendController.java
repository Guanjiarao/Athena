package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.insight.biz.domain.dto.RecommendQueryDTO;
import athena.insight.biz.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "洞察推荐接口")
@RestController
@RequestMapping("/athena/insight")
public class InsightRecommendController {

    @Resource
    private RecommendationService recommendationService;

    @Operation(summary = "获取推荐内容")
    @GetMapping("/recommend")
    public Result recommend(@Parameter(description = "推荐类型") @RequestParam("type") Byte type,
                            @Parameter(description = "频道ID") @RequestParam(value = "channelId", required = false) Integer channelId,
                            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
                            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
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
