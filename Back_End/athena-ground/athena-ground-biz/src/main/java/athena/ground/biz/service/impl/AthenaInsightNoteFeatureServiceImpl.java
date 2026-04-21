package athena.ground.biz.service.impl;

import athena.ground.biz.rpc.InsightFeatureFeignApi;
import athena.ground.biz.service.AthenaInsightNoteFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaInsightNoteFeatureServiceImpl implements AthenaInsightNoteFeatureService {

    private final InsightFeatureFeignApi insightFeignApi;

    @Override
    public void deleteByNoteId(Long noteId) {
        insightFeignApi.deleteByNoteId(noteId);
        log.info("[AthenaInsightNoteFeatureService] 删除 insight 内容特征成功, noteId={}", noteId);
    }
}
