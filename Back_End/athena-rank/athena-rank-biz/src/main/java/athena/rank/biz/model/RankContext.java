package athena.rank.biz.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RankContext {

    private final String scene;

    private final long baseTimeSeconds;

    private final long periodSeconds;

    private final int exactCapacity;

    public long resolvePeriodNo(long eventTimeMillis) {
        long currentSeconds = eventTimeMillis / 1000;
        return (currentSeconds - baseTimeSeconds) / periodSeconds + 1;
    }
}
