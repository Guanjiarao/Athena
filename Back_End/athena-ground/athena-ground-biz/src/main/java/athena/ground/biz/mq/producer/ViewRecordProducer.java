package athena.ground.biz.mq.producer;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.ground.biz.constant.ViewRecordConstants;
import athena.ground.biz.mq.event.ViewRecordEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 浏览记录 MQ 生产者
 */
@Slf4j
@Component
public class ViewRecordProducer {

    @Resource
    private MessageQueueProducer messageQueueProducer;

    /**
     * 发送浏览记录消息
     */
    public void sendViewMessage(Long userId, Long noteId, String viewTime, Integer duration) {
        ViewRecordEvent event = ViewRecordEvent.builder()
                .userId(userId)
                .noteId(noteId)
                .viewTime(viewTime)
                .duration(duration)
                .build();

        String keys = userId + ":" + noteId + ":" + viewTime;
        try {
            messageQueueProducer.send(
                    ViewRecordConstants.VIEW_RECORD_TOPIC,
                    keys,
                    ViewRecordConstants.VIEW_RECORD_BIZ_DESC,
                    event
            );
            log.info("[ViewRecordProducer] 发送成功, userId={}, noteId={}, duration={}s", userId, noteId, duration);
        } catch (Exception e) {
            log.error("[ViewRecordProducer] 发送失败, userId={}, noteId={}", userId, noteId, e);
        }
    }
}
