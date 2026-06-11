package athena.rank.biz.strategy;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RankSceneStrategyFactory {

    private final Map<String, RankSceneStrategy> strategyMap;

    public RankSceneStrategyFactory(List<RankSceneStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(RankSceneStrategy::scene, Function.identity()));
    }

    public RankSceneStrategy get(String scene) {
        if (!StringUtils.hasText(scene)) {
            throw new IllegalArgumentException("排行榜场景不能为空");
        }
        RankSceneStrategy strategy = strategyMap.get(scene);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的排行榜场景: " + scene);
        }
        return strategy;
    }
}
