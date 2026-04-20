package athena.ground.biz.mq.consumer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.constant.NoteInteractionConstants;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.mq.event.NoteInteractionEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 笔记互动 MQ 消费者 — 异步更新计数
 */
@Slf4j
@Component
public class NoteInteractionConsumer {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void onMessage(String message) {
        MessageWrapper<NoteInteractionEvent> wrapper = JsonUtils.parseObject(
                message,
                new TypeReference<MessageWrapper<NoteInteractionEvent>>() {
                }
        );
        NoteInteractionEvent event = wrapper.getBody();
        if (event == null) {
            log.warn("[NoteInteractionConsumer] 消息体为空, keys={}", wrapper.getKeys());
            return;
        }

        String eventId = event.getEventId();
        String idempotentKey = NoteInteractionConstants.NOTE_INTERACTION_IDEMPOTENT_KEY_PREFIX + eventId;
        Boolean firstConsume = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL);
        if (!Boolean.TRUE.equals(firstConsume)) {
            log.info("[NoteInteractionConsumer] 幂等命中, eventId={}, noteId={}, actionType={}",
                    eventId, event.getNoteId(), event.getActionType());
            return;
        }

        try {
            int affected = applyCount(event);
            log.info("[NoteInteractionConsumer] 处理成功, eventId={}, noteId={}, userId={}, actionType={}, delta={}, affected={}",
                    eventId, event.getNoteId(), event.getUserId(), event.getActionType(), event.getDelta(), affected);
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("[NoteInteractionConsumer] 处理失败, eventId={}, noteId={}, actionType={}",
                    eventId, event.getNoteId(), event.getActionType(), e);
            throw e;
        }
    }

    private int applyCount(NoteInteractionEvent event) {
        String actionType = event.getActionType();
        Long noteId = event.getNoteId();
        Long delta = event.getDelta();

        if (NoteInteractionConstants.ACTION_LIKE.equals(actionType)
                || NoteInteractionConstants.ACTION_UNLIKE.equals(actionType)) {
            return noteCountDOMapper.incrementLikeTotal(noteId, delta);
        }
        if (NoteInteractionConstants.ACTION_COLLECT.equals(actionType)
                || NoteInteractionConstants.ACTION_UNCOLLECT.equals(actionType)) {
            return noteCountDOMapper.incrementCollectTotal(noteId, delta);
        }
        throw new IllegalArgumentException("未知互动类型: " + actionType);
    }
}
