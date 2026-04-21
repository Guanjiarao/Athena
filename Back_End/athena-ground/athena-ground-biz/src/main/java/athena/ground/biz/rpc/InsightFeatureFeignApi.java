package athena.ground.biz.rpc;

import athena.athenaframework.result.Result;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InsightFeatureFeignApi {

    @Resource
    private athena.insight.api.InsightFeignApi insightFeignApi;

    public void refreshNoteFeature(Long noteId)
    {
        if(noteId==null)
        {
            return;
        }
        Result<?> result = insightFeignApi.refreshNoteFeature(Map.of("noteId", noteId));
        if(result==null||result.getCode()!=200)
        {
            return;
        }
    }

    public void deleteByNoteId(Long noteId)
    {
        if(noteId==null)
        {
            return;
        }
        Result<?> result = insightFeignApi.deleteNoteFeature(noteId);
        if(result==null||result.getCode()!=200)
        {
            throw new RuntimeException("调用 insight 内容特征删除接口失败：" + (result == null ? "返回为空" : result.getMessage()));
        }
    }
}
