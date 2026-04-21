package athena.comment.biz.rpc;

import athena.athenaframework.result.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GroundFeignApi {
    @Resource
    private athena.ground.api.GroundFeignApi groundRemoteFeignApi;

    public Result commentAdd(Long noteId,Long num)
    {
        return groundRemoteFeignApi.commentAdd(noteId, num);
    }
}