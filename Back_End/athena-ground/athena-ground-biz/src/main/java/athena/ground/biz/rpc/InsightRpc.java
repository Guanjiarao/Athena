package athena.ground.biz.rpc;

import athena.athenaframework.result.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class InsightRpc {

    @Resource
    private InsightFeignApi insightFeignApi;

    public void refreshNoteFeature(Long noteId) {
        if (noteId == null) {
            return;
        }
        try {
            Result<?> result = insightFeignApi.refreshNoteFeature(Map.of("noteId", noteId));
            if (result == null || result.getCode() != 200) {
                log.warn("[InsightRpc] 增量刷新内容特征失败, noteId={}, code={}", noteId, result == null ? null : result.getCode());
                return;
            }
            log.info("[InsightRpc] 增量刷新内容特征成功, noteId={}", noteId);
        } catch (Exception e) {
            log.error("[InsightRpc] 调用insight增量刷新内容特征异常, noteId={}", noteId, e);
        }
    }
}
