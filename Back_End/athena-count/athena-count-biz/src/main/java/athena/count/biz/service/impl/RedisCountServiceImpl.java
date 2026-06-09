package athena.count.biz.service.impl;

import athena.count.api.dto.CounterBatchDeltaDTO;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterQueryDTO;
import athena.count.api.dto.CounterValueDTO;
import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.producer.CountDeltaProducer;
import athena.count.biz.service.CountService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RedisCountServiceImpl implements CountService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CountDeltaProducer countDeltaProducer;

    @Override
    public void delta(CounterDeltaDTO deltaDTO) {
        validateDelta(deltaDTO);
        if (!StringUtils.hasText(deltaDTO.getEventId())) {
            deltaDTO.setEventId(UUID.randomUUID().toString());
        }
        countDeltaProducer.send(deltaDTO);
    }

    @Override
    public void batchDelta(CounterBatchDeltaDTO batchDeltaDTO) {
        if (batchDeltaDTO == null || CollectionUtils.isEmpty(batchDeltaDTO.getDeltas())) {
            return;
        }
        batchDeltaDTO.getDeltas().stream()
                .filter(Objects::nonNull)
                .forEach(this::delta);
    }

    @Override
    public void applyDelta(String scope, Long targetId, String counterType, Long delta) {
        validate(scope, targetId, counterType);
        if (delta == null || delta == 0L) {
            return;
        }
        String key = buildCounterKey(scope, targetId);
        stringRedisTemplate.opsForHash().increment(key, counterType, delta);
        stringRedisTemplate.opsForSet().add(CountConstants.COUNTER_DIRTY_SET_KEY, key);
    }

    @Override
    public CounterValueDTO getOne(String scope, Long targetId) {
        if (!StringUtils.hasText(scope) || targetId == null) {
            throw new IllegalArgumentException("scope 和 targetId 不能为空");
        }
        String key = buildCounterKey(scope, targetId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        CounterValueDTO valueDTO = new CounterValueDTO();
        valueDTO.setScope(scope);
        valueDTO.setTargetId(targetId);
        valueDTO.setCounters(toLongMap(entries));
        return valueDTO;
    }

    @Override
    public List<CounterValueDTO> batchGet(CounterQueryDTO queryDTO) {
        if (queryDTO == null || !StringUtils.hasText(queryDTO.getScope()) || CollectionUtils.isEmpty(queryDTO.getTargetIds())) {
            return Collections.emptyList();
        }
        return queryDTO.getTargetIds().stream()
                .filter(Objects::nonNull)
                .map(targetId -> getCounterValue(queryDTO.getScope(), targetId, queryDTO.getCounterTypes()))
                .collect(Collectors.toList());
    }

    private CounterValueDTO getCounterValue(String scope, Long targetId, List<String> counterTypes) {
        String key = buildCounterKey(scope, targetId);
        Map<String, Long> counters;
        if (CollectionUtils.isEmpty(counterTypes)) {
            counters = toLongMap(stringRedisTemplate.opsForHash().entries(key));
        } else {
            List<Object> rawValues = stringRedisTemplate.opsForHash().multiGet(key, new ArrayList<>(counterTypes));
            counters = new java.util.HashMap<>();
            for (int i = 0; i < counterTypes.size(); i++) {
                Object rawValue = rawValues == null ? null : rawValues.get(i);
                counters.put(counterTypes.get(i), parseLong(rawValue));
            }
        }
        CounterValueDTO valueDTO = new CounterValueDTO();
        valueDTO.setScope(scope);
        valueDTO.setTargetId(targetId);
        valueDTO.setCounters(counters);
        return valueDTO;
    }

    private Map<String, Long> toLongMap(Map<Object, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        return entries.entrySet().stream()
                .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), entry -> parseLong(entry.getValue())));
    }

    private long parseLong(Object rawValue) {
        if (rawValue == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(rawValue));
    }

    private void validateDelta(CounterDeltaDTO deltaDTO) {
        if (deltaDTO == null) {
            throw new IllegalArgumentException("计数变更不能为空");
        }
        validate(deltaDTO.getScope(), deltaDTO.getTargetId(), deltaDTO.getCounterType());
        if (deltaDTO.getDelta() == null) {
            throw new IllegalArgumentException("delta 不能为空");
        }
    }

    private void validate(String scope, Long targetId, String counterType) {
        if (!StringUtils.hasText(scope) || targetId == null || !StringUtils.hasText(counterType)) {
            throw new IllegalArgumentException("scope、targetId 和 counterType 不能为空");
        }
        Set<String> supportedScopes = Set.of(CountConstants.SCOPE_NOTE, CountConstants.SCOPE_COMMENT, CountConstants.SCOPE_USER);
        if (!supportedScopes.contains(scope)) {
            throw new IllegalArgumentException("不支持的计数域: " + scope);
        }
    }

    private String buildCounterKey(String scope, Long targetId) {
        return CountConstants.COUNTER_KEY_PREFIX + scope + ":" + targetId;
    }
}
