package athena.comment.biz.rpc;

import athena.athenaframework.result.Result;
import athena.ground.api.GroundFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GroundFeginApi {
    @Resource
    private GroundFeignApi groundFeignApi;

    public Result commentAdd(Long noteId,Long num)
    {
        return groundFeignApi.commentAdd(noteId, num);
    }
}
