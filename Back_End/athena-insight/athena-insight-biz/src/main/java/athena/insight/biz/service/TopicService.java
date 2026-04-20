package athena.insight.biz.service;

import athena.insight.biz.domain.dataobject.TopicDO;

import java.util.List;

public interface TopicService {

    List<TopicDO> listAllActiveTopics();

    List<TopicDO> listTopicsByNoteId(Long noteId);

    List<TopicDO> listTopicsByModeAndRecordItem(Byte modeType, Integer recordItemId);
}
