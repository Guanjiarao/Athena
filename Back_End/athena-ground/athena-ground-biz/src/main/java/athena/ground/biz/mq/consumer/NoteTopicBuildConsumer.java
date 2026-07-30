package athena.ground.biz.mq.consumer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.constant.NoteTopicBuildConstants;
import athena.ground.biz.mq.event.NoteTopicBuildEvent;
import athena.ground.biz.service.NoteTopicBuildService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class NoteTopicBuildConsumer {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @Resource
    private NoteTopicBuildService noteTopicBuildService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void onMessage(String message) {
        MessageWrapper<NoteTopicBuildEvent> wrapper = JsonUtils.parseObject(
                message,
                new TypeReference<MessageWrapper<NoteTopicBuildEvent>>() {
                }
        );
        NoteTopicBuildEvent event = wrapper.getBody();
        if (event == null) {
            log.warn("[NoteTopicBuildConsumer] 消息体为空, keys={}", wrapper.getKeys());
            return;
        }

        String eventId = event.getEventId();
        String idempotentKey = NoteTopicBuildConstants.NOTE_TOPIC_BUILD_IDEMPOTENT_KEY_PREFIX + eventId;
        Boolean firstConsume = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL);
        if (!Boolean.TRUE.equals(firstConsume)) {
            log.info("[NoteTopicBuildConsumer] 幂等命中, eventId={}, noteId={}", eventId, event.getNoteId());
            return;
        }

        try {
            noteTopicBuildService.rebuildTopicsForNote(event);
            log.info("[NoteTopicBuildConsumer] 处理成功, eventId={}, noteId={}", eventId, event.getNoteId());
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("[NoteTopicBuildConsumer] 处理失败, eventId={}, noteId={}", eventId, event.getNoteId(), e);
            throw e;
        }
    }
}
