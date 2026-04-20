package athena.ground.biz.service.impl;

import athena.ground.biz.constant.NoteTopicBuildConstants;
import athena.ground.biz.domain.dataobject.NoteTopicRelationDO;
import athena.ground.biz.domain.mapper.NoteTopicRelationMapper;
import athena.ground.biz.mq.event.NoteTopicBuildEvent;
import athena.ground.biz.rpc.InsightRpc;
import athena.ground.biz.service.NoteTopicBuildService;
import athena.ground.biz.service.NoteTopicExtractor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class NoteTopicBuildServiceImpl implements NoteTopicBuildService {

    @Resource
    private NoteTopicExtractor noteTopicExtractor;

    @Resource
    private NoteTopicRelationMapper noteTopicRelationMapper;

    @Resource
    private InsightRpc insightRpc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildTopicsForNote(NoteTopicBuildEvent event) {
        List<NoteTopicExtractor.TopicMatchResult> results = noteTopicExtractor.extract(event);
        int deleted = noteTopicRelationMapper.deleteByNoteId(event.getNoteId());
        if (results == null || results.isEmpty()) {
            log.warn("[NoteTopicBuildService] 未命中topic, noteId={}, title={}", event.getNoteId(), event.getTitle());
            return;
        }

        List<NoteTopicRelationDO> relations = results.stream().map(item -> {
            NoteTopicRelationDO relationDO = new NoteTopicRelationDO();
            relationDO.setNoteId(event.getNoteId());
            relationDO.setTopicId(item.topicId());
            relationDO.setWeight(item.weight());
            relationDO.setSourceType(NoteTopicBuildConstants.SOURCE_TYPE_RULE);
            return relationDO;
        }).toList();

        int inserted = noteTopicRelationMapper.batchInsert(relations);
        log.info("[NoteTopicBuildService] 重建完成, noteId={}, deleted={}, inserted={}, topics={}",
                event.getNoteId(),
                deleted,
                inserted,
                results.stream().map(NoteTopicExtractor.TopicMatchResult::topicName).toList());
        insightRpc.refreshNoteFeature(event.getNoteId());
    }
}
