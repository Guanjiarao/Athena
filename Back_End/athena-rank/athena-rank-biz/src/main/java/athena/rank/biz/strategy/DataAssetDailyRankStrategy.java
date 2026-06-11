package athena.rank.biz.strategy;

import athena.rank.api.constant.RankSceneConstants;
import athena.rank.biz.config.RankProperties;
import org.springframework.stereotype.Component;

@Component
public class DataAssetDailyRankStrategy extends AbstractDailyRankSceneStrategy {

    public DataAssetDailyRankStrategy(RankProperties rankProperties) {
        super(rankProperties);
    }

    @Override
    public String scene() {
        return RankSceneConstants.DATA_ASSET_DAILY;
    }
}
