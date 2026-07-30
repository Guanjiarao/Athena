package athena.rank.biz.strategy;

import athena.rank.biz.config.RankProperties;
import athena.rank.biz.constant.RankConstants;
import athena.rank.biz.model.RankContext;

public abstract class AbstractDailyRankSceneStrategy implements RankSceneStrategy {

    private final RankProperties rankProperties;

    protected AbstractDailyRankSceneStrategy(RankProperties rankProperties) {
        this.rankProperties = rankProperties;
    }

    @Override
    public RankContext context() {
        RankProperties.Leaderboard leaderboard = rankProperties.getLeaderboard();
        return RankContext.builder()
                .scene(scene())
                .baseTimeSeconds(leaderboard.getBaseTimeSeconds())
                .periodSeconds(RankConstants.DAY_SECONDS)
                .exactCapacity(leaderboard.getExactCapacity())
                .build();
    }
}
