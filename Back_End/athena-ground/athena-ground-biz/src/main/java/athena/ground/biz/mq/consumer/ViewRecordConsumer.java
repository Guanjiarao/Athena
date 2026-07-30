package athena.ground.biz.mq.consumer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.domain.mapper.UserViewRecordMapper;
import athena.ground.biz.mq.event.ViewRecordEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 浏览记录 MQ 消费者 — 异步落库 MySQL
 */
@Slf4j
@Component
public class ViewRecordConsumer {

    @Resource
    private UserViewRecordMapper userViewRecordMapper;

    public void onMessage(String message) {
        MessageWrapper<ViewRecordEvent> wrapper = JsonUtils.parseObject(message, MessageWrapper.class);
        ViewRecordEvent event = JsonUtils.parseObject(JsonUtils.toJsonString(wrapper.getBody()), ViewRecordEvent.class);
        try {
            log.info("[ViewRecordConsumer] 开始落库, userId={}, noteId={}, viewTime={}, duration={}s, keys={}",
                    event.getUserId(), event.getNoteId(), event.getViewTime(), event.getDuration(), wrapper.getKeys());
            userViewRecordMapper.upsertViewRecord(
                    event.getUserId(),
                    event.getNoteId(),
                    event.getViewTime(),
                    event.getDuration() == null ? 0 : event.getDuration()
            );
            log.info("[ViewRecordConsumer] 落库成功, userId={}, noteId={}", event.getUserId(), event.getNoteId());
        } catch (Exception e) {
            log.error("[ViewRecordConsumer] 落库失败, noteId={}, keys={}", event.getNoteId(), wrapper.getKeys(), e);
            throw e;
        }
    }
}
