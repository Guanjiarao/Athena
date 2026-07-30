package athena.ground.biz.mq.producer;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.ground.biz.constant.NoteTopicBuildConstants;
import athena.ground.biz.mq.event.NoteTopicBuildEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoteTopicBuildProducer {

    @Resource
    private MessageQueueProducer messageQueueProducer;

    public void send(NoteTopicBuildEvent event) {
        String keys = event.getNoteId() == null ? null : String.valueOf(event.getNoteId());
        try {
            messageQueueProducer.send(
                    NoteTopicBuildConstants.NOTE_TOPIC_BUILD_TOPIC,
                    keys,
                    NoteTopicBuildConstants.NOTE_TOPIC_BUILD_BIZ_DESC,
                    event
            );
            log.info("[NoteTopicBuildProducer] 发送成功, eventId={}, noteId={}", event.getEventId(), event.getNoteId());
        } catch (Exception e) {
            log.error("[NoteTopicBuildProducer] 发送失败, eventId={}, noteId={}", event.getEventId(), event.getNoteId(), e);
            throw e;
        }
    }
}
