package athena.insight.biz.service.impl;

import athena.insight.biz.domain.dataobject.NoteTopicRelationDO;
import athena.insight.biz.domain.dataobject.RecordTopicRelationDO;
import athena.insight.biz.domain.dataobject.TopicDO;
import athena.insight.biz.domain.mapper.NoteTopicRelationMapper;
import athena.insight.biz.domain.mapper.RecordTopicRelationMapper;
import athena.insight.biz.domain.mapper.TopicMapper;
import athena.insight.biz.service.TopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TopicServiceImpl implements TopicService {

    private static final byte STATUS_ACTIVE = 1;

    @Resource
    private TopicMapper topicMapper;

    @Resource
    private NoteTopicRelationMapper noteTopicRelationMapper;

    @Resource
    private RecordTopicRelationMapper recordTopicRelationMapper;

    @Override
    public List<TopicDO> listAllActiveTopics() {
        LambdaQueryWrapper<TopicDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicDO::getStatus, STATUS_ACTIVE)
                .orderByAsc(TopicDO::getSort)
                .orderByAsc(TopicDO::getId);
        return topicMapper.selectList(queryWrapper);
    }

    @Override
    public List<TopicDO> listTopicsByNoteId(Long noteId) {
        if (noteId == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<NoteTopicRelationDO> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(NoteTopicRelationDO::getNoteId, noteId)
                .orderByDesc(NoteTopicRelationDO::getWeight)
                .orderByAsc(NoteTopicRelationDO::getId);
        List<NoteTopicRelationDO> relations = noteTopicRelationMapper.selectList(relationQuery);
        if (relations == null || relations.isEmpty()) {
            log.debug("[TopicService] note未配置topic关系, noteId={}", noteId);
            return Collections.emptyList();
        }

        List<Long> topicIds = relations.stream()
                .map(NoteTopicRelationDO::getTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (topicIds.isEmpty()) {
            log.debug("[TopicService] note关系存在但topicId为空, noteId={}", noteId);
            return Collections.emptyList();
        }

        LambdaQueryWrapper<TopicDO> topicQuery = new LambdaQueryWrapper<>();
        topicQuery.in(TopicDO::getId, topicIds)
                .eq(TopicDO::getStatus, STATUS_ACTIVE)
                .orderByAsc(TopicDO::getSort)
                .orderByAsc(TopicDO::getId);
        List<TopicDO> topics = topicMapper.selectList(topicQuery);
        if (topics == null || topics.isEmpty()) {
            log.debug("[TopicService] note已有关联topicId但未查到启用topic, noteId={}, topicIds={}", noteId, topicIds);
            return Collections.emptyList();
        }
        return topics;
    }

    @Override
    public List<TopicDO> listTopicsByModeAndRecordItem(Byte modeType, Integer recordItemId) {
        if (modeType == null || recordItemId == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RecordTopicRelationDO> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(RecordTopicRelationDO::getModeType, modeType)
                .eq(RecordTopicRelationDO::getRecordItemId, recordItemId)
                .orderByDesc(RecordTopicRelationDO::getWeight)
                .orderByAsc(RecordTopicRelationDO::getId);
        List<RecordTopicRelationDO> relations = recordTopicRelationMapper.selectList(relationQuery);
        if (relations == null || relations.isEmpty()) {
            log.debug("[TopicService] record未配置topic关系, modeType={}, recordItemId={}", modeType, recordItemId);
            return Collections.emptyList();
        }

        List<Long> topicIds = relations.stream()
                .map(RecordTopicRelationDO::getTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (topicIds.isEmpty()) {
            log.debug("[TopicService] record关系存在但topicId为空, modeType={}, recordItemId={}", modeType, recordItemId);
            return Collections.emptyList();
        }

        LambdaQueryWrapper<TopicDO> topicQuery = new LambdaQueryWrapper<>();
        topicQuery.in(TopicDO::getId, topicIds)
                .eq(TopicDO::getStatus, STATUS_ACTIVE)
                .orderByAsc(TopicDO::getSort)
                .orderByAsc(TopicDO::getId);
        List<TopicDO> topics = topicMapper.selectList(topicQuery);
        if (topics == null || topics.isEmpty()) {
            log.debug("[TopicService] record已有关联topicId但未查到启用topic, modeType={}, recordItemId={}, topicIds={}", modeType, recordItemId, topicIds);
            return Collections.emptyList();
        }
        return topics;
    }
}
