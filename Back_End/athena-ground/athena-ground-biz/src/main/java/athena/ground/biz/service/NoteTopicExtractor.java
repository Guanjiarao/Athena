package athena.ground.biz.service;

import athena.ground.biz.mq.event.NoteTopicBuildEvent;

import java.math.BigDecimal;
import java.util.List;

public interface NoteTopicExtractor {

    List<TopicMatchResult> extract(NoteTopicBuildEvent event);

    record TopicMatchResult(Long topicId, String topicName, BigDecimal weight) {
    }
}
