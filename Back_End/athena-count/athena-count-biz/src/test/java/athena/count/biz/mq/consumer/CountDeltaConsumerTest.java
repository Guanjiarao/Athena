package athena.count.biz.mq.consumer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.event.CountDeltaEvent;
import athena.count.biz.service.CountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CountDeltaConsumerTest {

    private CountDeltaConsumer consumer;
    private CountService countService;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        countService = mock(CountService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        consumer = new CountDeltaConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "countService", countService);
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "stringRedisTemplate", stringRedisTemplate);
    }

    @Test
    void onMessageShouldApplyDeltaForFirstConsume() {
        when(valueOperations.setIfAbsent(eq("athena:count:idempotent:evt-1"), eq("1"), any(Duration.class))).thenReturn(true);

        consumer.onMessage(buildMessage("evt-1"));

        verify(countService).applyDelta(CountConstants.SCOPE_NOTE, 100L, CountConstants.NOTE_LIKE_TOTAL, 1L);
    }

    @Test
    void onMessageShouldSkipDuplicatedEvent() {
        when(valueOperations.setIfAbsent(eq("athena:count:idempotent:evt-1"), eq("1"), any(Duration.class))).thenReturn(false);

        consumer.onMessage(buildMessage("evt-1"));

        verify(countService, never()).applyDelta(any(), any(), any(), any());
    }

    private String buildMessage(String eventId) {
        CountDeltaEvent event = new CountDeltaEvent();
        event.setEventId(eventId);
        event.setScope(CountConstants.SCOPE_NOTE);
        event.setTargetId(100L);
        event.setCounterType(CountConstants.NOTE_LIKE_TOTAL);
        event.setDelta(1L);
        event.setTimestamp(System.currentTimeMillis());

        MessageWrapper<CountDeltaEvent> wrapper = new MessageWrapper<>();
        wrapper.setKeys(eventId);
        wrapper.setBody(event);
        return JsonUtils.toJsonString(wrapper);
    }
}
