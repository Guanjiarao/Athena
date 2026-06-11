package athena.rank.biz.strategy;

import athena.rank.api.constant.RankSceneConstants;
import athena.rank.biz.config.RankProperties;
import org.springframework.stereotype.Component;

@Component
public class NewcomerDailyRankStrategy extends AbstractDailyRankSceneStrategy {

    public NewcomerDailyRankStrategy(RankProperties rankProperties) {
        super(rankProperties);
    }

    @Override
    public String scene() {
        return RankSceneConstants.NEWCOMER_DAILY;
    }
}
