package athena.ground.biz.mq.producer;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.ground.biz.mq.event.AthenaNoteSyncEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Athena 笔记知识同步 MQ 生产者
 */
@Slf4j
@Component
public class AthenaNoteSyncProducer {

    private static final String NOTE_SYNC_TOPIC = "note-knowledge-sync";
    private static final String NOTE_SYNC_BIZ_DESC = "Athena 笔记知识同步";

    @Resource
    private MessageQueueProducer messageQueueProducer;

    public void send(AthenaNoteSyncEvent event) {
        String keys = event.getNoteId() == null ? null : String.valueOf(event.getNoteId());
        try {
            messageQueueProducer.send(NOTE_SYNC_TOPIC, keys, NOTE_SYNC_BIZ_DESC, event);
            log.info("[AthenaNoteSyncProducer] 发送成功, noteId={}, type={}", event.getNoteId(), event.getType());
        } catch (Exception e) {
            log.error("[AthenaNoteSyncProducer] 发送失败, noteId={}", event.getNoteId(), e);
            throw e;
        }
    }
}
