package athena.ground.biz.service.impl;

import athena.ground.biz.config.NoteTopicExtractorProperties;
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
import java.util.stream.Collectors;

@Service
public class RuleBasedNoteTopicExtractor implements NoteTopicExtractor {

    @Resource
    private TopicMapper topicMapper;

    @Resource
    private NoteTopicExtractorProperties noteTopicExtractorProperties;

    @Override
    public List<TopicMatchResult> extract(NoteTopicBuildEvent event) {
        String title = normalize(event.getTitle());
        String content = normalize(event.getContent());
        Map<String, BigDecimal> topicWeights = new LinkedHashMap<>();

        for (NoteTopicExtractorProperties.TopicRule rule : noteTopicExtractorProperties.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getTopicName())) {
                continue;
            }
            addWeightIfMatches(topicWeights,
                    rule.getTopicName(),
                    title,
                    content,
                    rule.getKeywords(),
                    defaultWeight(rule.getTitleWeight(), "1.0"),
                    defaultWeight(rule.getContentWeight(), "0.6"));
        }

        if (event.getChannelId() != null) {
            for (NoteTopicExtractorProperties.ChannelBoostRule boostRule : noteTopicExtractorProperties.getChannelBoosts()) {
                if (boostRule == null || boostRule.getChannelId() == null || !StringUtils.hasText(boostRule.getTopicName())) {
                    continue;
                }
                if (event.getChannelId().equals(boostRule.getChannelId())) {
                    topicWeights.merge(boostRule.getTopicName(), defaultWeight(boostRule.getWeight(), "0.2"), BigDecimal::add);
                }
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
                                    List<String> keywords,
                                    BigDecimal titleWeight,
                                    BigDecimal contentWeight) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        BigDecimal weight = BigDecimal.ZERO;
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (!StringUtils.hasText(normalizedKeyword)) {
                continue;
            }
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

    private BigDecimal defaultWeight(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.toLowerCase(Locale.ROOT) : "";
    }
}
