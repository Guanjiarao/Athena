package athena.rank.biz.repository;

import athena.rank.api.dto.RankItemDTO;
import athena.rank.biz.config.RankProperties;
import athena.rank.biz.constant.RankConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class ExactRankRepository {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RankProperties rankProperties;

    public boolean updateScoreOnce(String requestId, String rankKey, Long memberId, Long delta, long nowSeconds) {
        String idempotentKey = RankConstants.RANK_IDEMPOTENT_KEY_PREFIX + requestId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofSeconds(rankProperties.getLeaderboard().getIdempotentExpireSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            return false;
        }
        Double oldRawScore = stringRedisTemplate.opsForZSet().score(rankKey, String.valueOf(memberId));
        long oldScore = oldRawScore == null ? 0L : oldRawScore.longValue();
        long newScore = oldScore + delta;
        double rawScore = mergeRawScore(newScore, nowSeconds);
        stringRedisTemplate.opsForZSet().add(rankKey, String.valueOf(memberId), rawScore);
        return true;
    }

    public List<RankItemDTO> top(String rankKey, int start, int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, start, start + size - 1L);
        if (CollectionUtils.isEmpty(tuples)) {
            return Collections.emptyList();
        }
        List<RankItemDTO> items = new ArrayList<>();
        long rankNo = start + 1L;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            RankItemDTO item = new RankItemDTO();
            item.setMemberId(Long.valueOf(tuple.getValue()));
            item.setRankNo(rankNo++);
            item.setRawScore(tuple.getScore());
            item.setScore(tuple.getScore() == null ? 0L : tuple.getScore().longValue());
            item.setEstimated(false);
            items.add(item);
        }
        return items;
    }

    public Long rankNo(String rankKey, Long memberId) {
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(rankKey, String.valueOf(memberId));
        return rank == null ? null : rank + 1;
    }

    public Long score(String rankKey, Long memberId) {
        Double rawScore = stringRedisTemplate.opsForZSet().score(rankKey, String.valueOf(memberId));
        return rawScore == null ? null : rawScore.longValue();
    }

    public long exactSize(String rankKey) {
        Long size = stringRedisTemplate.opsForZSet().zCard(rankKey);
        return size == null ? 0L : size;
    }

    public void trim(String rankKey, int exactCapacity) {
        long size = exactSize(rankKey);
        if (size <= exactCapacity) {
            return;
        }
        stringRedisTemplate.opsForZSet().removeRange(rankKey, 0, size - exactCapacity - 1);
    }

    public void replace(String rankKey, Long memberId, Long score, long nowSeconds) {
        stringRedisTemplate.opsForZSet().add(rankKey, String.valueOf(memberId), mergeRawScore(score, nowSeconds));
    }

    private double mergeRawScore(long score, long nowSeconds) {
        long tieBreak = Math.max(0, rankProperties.getLeaderboard().getTieBreakFutureSeconds() - nowSeconds);
        return score + tieBreak / 10_000_000_000D;
    }
}
