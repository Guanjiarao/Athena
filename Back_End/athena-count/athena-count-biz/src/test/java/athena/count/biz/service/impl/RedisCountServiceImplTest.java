package athena.count.biz.service.impl;

import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterQueryDTO;
import athena.count.api.dto.CounterValueDTO;
import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.producer.CountDeltaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCountServiceImplTest {

    private RedisCountServiceImpl countService;
    private StringRedisTemplate stringRedisTemplate;
    private CountDeltaProducer countDeltaProducer;
    private HashOperations<String, Object, Object> hashOperations;
    private SetOperations<String, String> setOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        countDeltaProducer = mock(CountDeltaProducer.class);
        hashOperations = mock(HashOperations.class);
        setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        countService = new RedisCountServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(countService, "stringRedisTemplate", stringRedisTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(countService, "countDeltaProducer", countDeltaProducer);
    }

    @Test
    void deltaShouldFillEventIdAndSendAsyncEvent() {
        CounterDeltaDTO deltaDTO = new CounterDeltaDTO();
        deltaDTO.setScope(CountConstants.SCOPE_NOTE);
        deltaDTO.setTargetId(100L);
        deltaDTO.setCounterType(CountConstants.NOTE_LIKE_TOTAL);
        deltaDTO.setDelta(1L);

        countService.delta(deltaDTO);

        ArgumentCaptor<CounterDeltaDTO> captor = ArgumentCaptor.forClass(CounterDeltaDTO.class);
        verify(countDeltaProducer).send(captor.capture());
        assertTrue(captor.getValue().getEventId() != null && !captor.getValue().getEventId().isBlank());
    }

    @Test
    void applyDeltaShouldIncrementHashFieldAndRecordDirtyKey() {
        countService.applyDelta(CountConstants.SCOPE_NOTE, 100L, CountConstants.NOTE_LIKE_TOTAL, 5L);

        verify(hashOperations).increment("athena:count:note:100", CountConstants.NOTE_LIKE_TOTAL, 5L);
        verify(setOperations).add(CountConstants.COUNTER_DIRTY_SET_KEY, "athena:count:note:100");
    }

    @Test
    void getOneShouldReadAllCounterFieldsFromHash() {
        when(hashOperations.entries("athena:count:user:8")).thenReturn(Map.of(
                CountConstants.USER_FOLLOWER_TOTAL, "11",
                CountConstants.USER_LIKED_TOTAL, "30"
        ));

        CounterValueDTO result = countService.getOne(CountConstants.SCOPE_USER, 8L);

        assertEquals(CountConstants.SCOPE_USER, result.getScope());
        assertEquals(8L, result.getTargetId());
        assertEquals(11L, result.getCounters().get(CountConstants.USER_FOLLOWER_TOTAL));
        assertEquals(30L, result.getCounters().get(CountConstants.USER_LIKED_TOTAL));
    }

    @Test
    void batchGetShouldUseMultiGetWhenCounterTypesSpecified() {
        CounterQueryDTO queryDTO = new CounterQueryDTO();
        queryDTO.setScope(CountConstants.SCOPE_NOTE);
        queryDTO.setTargetIds(List.of(1L, 2L));
        queryDTO.setCounterTypes(List.of(CountConstants.NOTE_LIKE_TOTAL, CountConstants.NOTE_COLLECT_TOTAL));
        when(hashOperations.multiGet(eq("athena:count:note:1"), eq(List.of(CountConstants.NOTE_LIKE_TOTAL, CountConstants.NOTE_COLLECT_TOTAL))))
                .thenReturn(List.of("3", "4"));
        when(hashOperations.multiGet(eq("athena:count:note:2"), eq(List.of(CountConstants.NOTE_LIKE_TOTAL, CountConstants.NOTE_COLLECT_TOTAL))))
                .thenReturn(List.of("5", "6"));

        List<CounterValueDTO> result = countService.batchGet(queryDTO);

        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).getCounters().get(CountConstants.NOTE_LIKE_TOTAL));
        assertEquals(6L, result.get(1).getCounters().get(CountConstants.NOTE_COLLECT_TOTAL));
    }

    @Test
    void applyDeltaShouldRejectUnsupportedScope() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> countService.applyDelta("unknown", 1L, CountConstants.NOTE_LIKE_TOTAL, 1L));

        assertTrue(exception.getMessage().contains("不支持的计数域"));
    }
}
