package athena.ground.biz.service.impl;

import athena.ground.biz.domain.dataobject.TopicDO;
import athena.ground.biz.domain.mapper.TopicMapper;
import athena.ground.biz.mq.event.NoteTopicBuildEvent;
import athena.ground.biz.service.NoteTopicExtractor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleBasedNoteTopicExtractor implements NoteTopicExtractor {

    @Resource
    private TopicMapper topicMapper;

    @Override
    public List<TopicMatchResult> extract(NoteTopicBuildEvent event) {
        String title = normalize(event.getTitle());
        String content = normalize(event.getContent());
        Map<String, BigDecimal> topicWeights = new LinkedHashMap<>();

        addWeightIfMatches(topicWeights, "经期护理", title, content, Set.of("经期", "姨妈", "生理期", "月经"), new BigDecimal("1.0"), new BigDecimal("0.6"));
        addWeightIfMatches(topicWeights, "痛经缓解", title, content, Set.of("痛经", "腹痛", "热敷", "缓解疼痛", "姨妈痛"), new BigDecimal("1.0"), new BigDecimal("0.6"));
        addWeightIfMatches(topicWeights, "睡眠调节", title, content, Set.of("失眠", "睡不好", "睡不着", "熬夜", "睡眠"), new BigDecimal("1.0"), new BigDecimal("0.6"));
        addWeightIfMatches(topicWeights, "情绪调节", title, content, Set.of("焦虑", "烦躁", "情绪低落", "压力", "崩溃"), new BigDecimal("1.0"), new BigDecimal("0.6"));
        addWeightIfMatches(topicWeights, "饮食管理", title, content, Set.of("饮食", "忌口", "补铁", "吃什么", "食物"), new BigDecimal("1.0"), new BigDecimal("0.6"));

        if (event.getChannelId() != null) {
            if (event.getChannelId() == 4) {
                topicWeights.merge("经期护理", new BigDecimal("0.4"), BigDecimal::add);
            }
            if (event.getChannelId() == 2) {
                topicWeights.merge("饮食管理", new BigDecimal("0.2"), BigDecimal::add);
            }
        }

        if (topicWeights.isEmpty()) {
            return List.of();
        }

        List<String> topicNames = new ArrayList<>(topicWeights.keySet());
        List<TopicDO> topics = topicMapper.selectActiveByNames(topicNames);
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }

        Map<String, TopicDO> topicMap = topics.stream().collect(Collectors.toMap(TopicDO::getTopicName, item -> item, (a, b) -> a, LinkedHashMap::new));
        return topicWeights.entrySet().stream()
                .filter(entry -> topicMap.containsKey(entry.getKey()))
                .map(entry -> new TopicMatchResult(
                        topicMap.get(entry.getKey()).getId(),
                        entry.getKey(),
                        entry.getValue().setScale(4, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private void addWeightIfMatches(Map<String, BigDecimal> topicWeights,
                                    String topicName,
                                    String title,
                                    String content,
                                    Set<String> keywords,
                                    BigDecimal titleWeight,
                                    BigDecimal contentWeight) {
        BigDecimal weight = BigDecimal.ZERO;
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (StringUtils.hasText(title) && title.contains(normalizedKeyword)) {
                weight = weight.add(titleWeight);
            }
            if (StringUtils.hasText(content) && content.contains(normalizedKeyword)) {
                weight = weight.add(contentWeight);
            }
        }
        if (weight.compareTo(BigDecimal.ZERO) > 0) {
            topicWeights.merge(topicName, weight, BigDecimal::add);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.toLowerCase(Locale.ROOT) : "";
    }
}
