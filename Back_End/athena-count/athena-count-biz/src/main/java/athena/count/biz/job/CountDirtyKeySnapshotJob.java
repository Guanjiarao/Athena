package athena.count.biz.job;

import athena.count.biz.constant.CountConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class CountDirtyKeySnapshotJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelayString = "${athena.count.snapshot-log-delay:60000}")
    public void logDirtyCounterSnapshot() {
        Long dirtySize = stringRedisTemplate.opsForSet().size(CountConstants.COUNTER_DIRTY_SET_KEY);
        if (dirtySize == null || dirtySize == 0L) {
            return;
        }
        Set<String> sampleKeys = stringRedisTemplate.opsForSet().distinctRandomMembers(CountConstants.COUNTER_DIRTY_SET_KEY, 20);
        log.info("[CountDirtyKeySnapshotJob] 当前计数脏 key 数量={}, sampleKeys={}", dirtySize, sampleKeys);
    }
}
