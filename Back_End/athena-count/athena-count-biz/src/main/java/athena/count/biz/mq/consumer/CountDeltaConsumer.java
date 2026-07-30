package athena.count.biz.mq.consumer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.event.CountDeltaEvent;
import athena.count.biz.service.CountService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Component
public class CountDeltaConsumer {

    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @Resource
    private CountService countService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void onMessage(String message) {
        MessageWrapper<CountDeltaEvent> wrapper = JsonUtils.parseObject(
                message,
                new TypeReference<MessageWrapper<CountDeltaEvent>>() {
                }
        );
        CountDeltaEvent event = wrapper.getBody();
        if (event == null) {
            log.warn("[CountDeltaConsumer] 消息体为空, keys={}", wrapper.getKeys());
            return;
        }
        if (!StringUtils.hasText(event.getEventId())) {
            throw new IllegalArgumentException("计数事件 eventId 不能为空");
        }
        String idempotentKey = CountConstants.IDEMPOTENT_KEY_PREFIX + event.getEventId();
        Boolean firstConsume = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL);
        if (!Boolean.TRUE.equals(firstConsume)) {
            log.info("[CountDeltaConsumer] 幂等命中, eventId={}, scope={}, targetId={}, counterType={}",
                    event.getEventId(), event.getScope(), event.getTargetId(), event.getCounterType());
            return;
        }
        try {
            countService.applyDelta(event.getScope(), event.getTargetId(), event.getCounterType(), event.getDelta());
            log.info("[CountDeltaConsumer] 处理成功, eventId={}, scope={}, targetId={}, counterType={}, delta={}",
                    event.getEventId(), event.getScope(), event.getTargetId(), event.getCounterType(), event.getDelta());
        } catch (Exception e) {
            stringRedisTemplate.delete(idempotentKey);
            log.error("[CountDeltaConsumer] 处理失败, eventId={}, scope={}, targetId={}, counterType={}",
                    event.getEventId(), event.getScope(), event.getTargetId(), event.getCounterType(), e);
            throw e;
        }
    }
}
