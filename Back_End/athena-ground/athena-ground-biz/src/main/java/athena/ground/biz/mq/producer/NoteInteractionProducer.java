package athena.ground.biz.mq.producer;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.ground.biz.constant.NoteInteractionConstants;
import athena.ground.biz.mq.event.NoteInteractionEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 笔记互动 MQ 生产者
 */
@Slf4j
@Component
public class NoteInteractionProducer {

    @Resource
    private MessageQueueProducer messageQueueProducer;

    public void send(NoteInteractionEvent event) {
        String keys = event.getEventId();
        try {
            messageQueueProducer.send(
                    NoteInteractionConstants.NOTE_INTERACTION_TOPIC,
                    keys,
                    NoteInteractionConstants.NOTE_INTERACTION_BIZ_DESC,
                    event
            );
            log.info("[NoteInteractionProducer] 发送成功, eventId={}, noteId={}, userId={}, actionType={}, delta={}",
                    event.getEventId(), event.getNoteId(), event.getUserId(), event.getActionType(), event.getDelta());
        } catch (Exception e) {
            log.error("[NoteInteractionProducer] 发送失败, eventId={}, noteId={}, actionType={}",
                    event.getEventId(), event.getNoteId(), event.getActionType(), e);
            throw e;
        }
    }
}
