package athena.rank.biz.repository;

import athena.rank.biz.constant.RankConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SegmentTreeRankRepository {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void applyDelta(String scene, long periodNo, long oldScore, long newScore) {
        String key = buildKey(scene, periodNo);
        if (oldScore > 0) {
            stringRedisTemplate.opsForHash().increment(key, bucket(oldScore), -1L);
        }
        if (newScore > 0) {
            stringRedisTemplate.opsForHash().increment(key, bucket(newScore), 1L);
        }
    }

    public long estimateBetterCount(String scene, long periodNo, long score) {
        String key = buildKey(scene, periodNo);
        long betterCount = 0L;
        long currentBucket = bucket(score);
        for (long bucket = currentBucket + 1; bucket <= maxBucket(); bucket++) {
            Object raw = stringRedisTemplate.opsForHash().get(key, String.valueOf(bucket));
            if (raw != null) {
                betterCount += Long.parseLong(String.valueOf(raw));
            }
        }
        return betterCount;
    }

    private String buildKey(String scene, long periodNo) {
        return RankConstants.RANK_SEGMENT_TREE_KEY_PREFIX + scene + ":" + periodNo;
    }

    /**
     * 当前先抽象成桶式线段树接口：外部调用不感知具体树结构，后续可替换成真正数组线段树。
     */
    private long bucket(long score) {
        return Math.max(0, score / 100);
    }

    private long maxBucket() {
        return 1_000_000L;
    }
}
