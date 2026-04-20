package athena.insight.biz.service.impl;

import athena.insight.biz.domain.dataobject.UserInsightDO;
import athena.insight.biz.domain.mapper.UserInsightMapper;
import athena.insight.biz.domain.vo.UserFeatureSnapshotVO;
import athena.insight.biz.domain.vo.UserInsightVO;
import athena.insight.biz.service.UserFeatureService;
import athena.insight.biz.service.UserInsightService;
import athena.insight.biz.util.JsonHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserInsightServiceImpl implements UserInsightService {

    @Resource
    private UserInsightMapper userInsightMapper;

    @Resource
    private UserFeatureService userFeatureService;

    @Override
    public UserInsightVO getInsight(Long userId) {
        if (userId == null) {
            return new UserInsightVO();
        }
        UserInsightDO insightDO = findByUserId(userId);
        return toVO(insightDO);
    }

    @Override
    public UserInsightVO refreshInsight(Long userId) {
        if (userId == null) {
            return new UserInsightVO();
        }

        UserInsightDO insightDO = findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        UserFeatureSnapshotVO featureSnapshotVO = userFeatureService.getSnapshot(userId);
        String behaviorJson = featureSnapshotVO == null ? null : featureSnapshotVO.getBehaviorFeatureJson();
        String healthJson = featureSnapshotVO == null ? null : featureSnapshotVO.getHealthFeatureJson();

        String healthFocusJson = buildHealthFocusJson(healthJson, behaviorJson);
        String contentFocusJson = buildContentFocusJson(behaviorJson, healthJson);
        String riskTagsJson = buildRiskTagsJson(behaviorJson, healthJson);
        String recommendationReasonsJson = buildRecommendationReasonsJson(behaviorJson, healthJson);

        if (insightDO == null) {
            insightDO = new UserInsightDO();
            insightDO.setUserId(userId);
            insightDO.setHealthFocusJson(healthFocusJson);
            insightDO.setContentFocusJson(contentFocusJson);
            insightDO.setRiskTagsJson(riskTagsJson);
            insightDO.setRecommendationReasonsJson(recommendationReasonsJson);
            insightDO.setInsightVersion(1);
            insightDO.setGeneratedAt(now);
            userInsightMapper.insert(insightDO);
        } else {
            insightDO.setHealthFocusJson(defaultArrayJson(healthFocusJson));
            insightDO.setContentFocusJson(defaultArrayJson(contentFocusJson));
            insightDO.setRiskTagsJson(defaultArrayJson(riskTagsJson));
            insightDO.setRecommendationReasonsJson(defaultArrayJson(recommendationReasonsJson));
            insightDO.setGeneratedAt(now);
            if (insightDO.getInsightVersion() == null) {
                insightDO.setInsightVersion(1);
            }
            userInsightMapper.updateById(insightDO);
        }

        return toVO(insightDO);
    }

    private UserInsightDO findByUserId(Long userId) {
        LambdaQueryWrapper<UserInsightDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInsightDO::getUserId, userId)
                .last("limit 1");
        return userInsightMapper.selectOne(queryWrapper);
    }

    private UserInsightVO toVO(UserInsightDO insightDO) {
        UserInsightVO vo = new UserInsightVO();
        if (insightDO == null) {
            return vo;
        }
        vo.setUserId(insightDO.getUserId());
        vo.setHealthFocusJson(insightDO.getHealthFocusJson());
        vo.setContentFocusJson(insightDO.getContentFocusJson());
        vo.setRiskTagsJson(insightDO.getRiskTagsJson());
        vo.setRecommendationReasonsJson(insightDO.getRecommendationReasonsJson());
        vo.setGeneratedAt(insightDO.getGeneratedAt() == null ? null : insightDO.getGeneratedAt().toString());
        return vo;
    }

    private String buildHealthFocusJson(String healthJson, String behaviorJson) {
        List<String> values = new ArrayList<>();
        JsonNode healthNode = JsonHelper.readTree(healthJson);
        Integer currentModeType = JsonHelper.getInt(healthNode, "currentModeType");
        Integer averageCycleLength = JsonHelper.getInt(healthNode, "averageCycleLength");
        Integer averageDurationDays = JsonHelper.getInt(healthNode, "averageDurationDays");
        Integer recordDays30d = JsonHelper.getInt(healthNode, "recordDays30d");
        List<String> symptomTopics = JsonHelper.getStringArray(healthNode, "symptomTopics");

        if (currentModeType != null) {
            values.add(switch (currentModeType) {
                case 1 -> "当前记录以经期模式为主，可持续关注周期稳定性";
                case 2 -> "当前记录以备孕模式为主，可持续关注身体节律变化";
                case 3 -> "当前记录以怀孕模式为主，建议持续关注日常状态变化";
                default -> "当前健康记录已有明确模式特征";
            });
        }
        if (averageCycleLength != null && averageDurationDays != null) {
            values.add("当前周期统计已形成，平均周期约" + averageCycleLength + "天，经期约" + averageDurationDays + "天");
        }
        if (!symptomTopics.isEmpty()) {
            values.add("近期健康记录主要集中在“" + symptomTopics.get(0) + "”相关主题");
        }
        if (recordDays30d != null && recordDays30d >= 7) {
            values.add("近30天记录较连续，可用于持续观察健康趋势");
        }
        if (values.isEmpty()) {
            values.addAll(parseTopTopics(behaviorJson, 2));
        }
        return toStringArrayJson(values.stream().distinct().toList());
    }

    private String buildContentFocusJson(String behaviorJson, String healthJson) {
        List<String> values = new ArrayList<>(parseTopTopics(behaviorJson, 3));
        JsonNode healthNode = JsonHelper.readTree(healthJson);
        List<String> symptomTopics = JsonHelper.getStringArray(healthNode, "symptomTopics");
        values.addAll(symptomTopics.stream().limit(2).toList());
        return values.isEmpty() ? "[]" : toStringArrayJson(values.stream().distinct().toList());
    }

    private String buildRiskTagsJson(String behaviorJson, String healthJson) {
        List<String> tags = new ArrayList<>();
        JsonNode behaviorNode = JsonHelper.readTree(behaviorJson);
        JsonNode healthNode = JsonHelper.readTree(healthJson);

        Integer viewCount30d = JsonHelper.getInt(behaviorNode, "viewCount30d");
        Integer likeCount30d = JsonHelper.getInt(behaviorNode, "likeCount30d");
        Integer collectCount30d = JsonHelper.getInt(behaviorNode, "collectCount30d");
        if ((viewCount30d == null || viewCount30d == 0)
                && (likeCount30d == null || likeCount30d == 0)
                && (collectCount30d == null || collectCount30d == 0)) {
            tags.add("内容行为数据较少");
        }

        Integer recordDays30d = JsonHelper.getInt(healthNode, "recordDays30d");
        Integer averageCycleLength = JsonHelper.getInt(healthNode, "averageCycleLength");
        if (recordDays30d == null || recordDays30d == 0) {
            tags.add("健康记录数据不足");
        }
        if (averageCycleLength == null) {
            tags.add("周期统计样本不足");
        }
        if (tags.isEmpty()) {
            tags.add("当前整体状态稳定");
        }
        return toStringArrayJson(tags);
    }

    private String buildRecommendationReasonsJson(String behaviorJson, String healthJson) {
        List<String> reasons = new ArrayList<>();
        Integer preferredType = parseTopType(behaviorJson);
        if (preferredType != null) {
            reasons.add(switch (preferredType) {
                case 0 -> "近期更偏好科普内容，推荐会优先考虑知识型内容";
                case 1 -> "近期更偏好图文内容，推荐会优先考虑图文内容";
                case 2 -> "近期更偏好视频内容，推荐会优先考虑视频内容";
                default -> "近期内容偏好已纳入推荐排序";
            });
        }
        JsonNode healthNode = JsonHelper.readTree(healthJson);
        List<String> symptomTopics = JsonHelper.getStringArray(healthNode, "symptomTopics");
        if (!symptomTopics.isEmpty()) {
            reasons.add("近期健康记录显示你更关注“" + symptomTopics.get(0) + "”相关内容");
        } else {
            List<String> topTopics = parseTopTopics(behaviorJson, 2);
            if (!topTopics.isEmpty()) {
                reasons.add("近期你更关注“" + topTopics.get(0) + "”相关主题");
            }
        }
        if (reasons.isEmpty()) {
            reasons.add("当前推荐结果主要基于通用热度与内容质量生成");
        }
        return toStringArrayJson(reasons);
    }

    private List<String> parseTopTopics(String behaviorJson, int limit) {
        if (!StringUtils.hasText(behaviorJson) || limit <= 0) {
            return List.of();
        }
        JsonNode behaviorNode = JsonHelper.readTree(behaviorJson);
        JsonNode topicPreferenceNode = JsonHelper.get(behaviorNode, "topicPreference");
        if (topicPreferenceNode == null || !topicPreferenceNode.isArray()) {
            return List.of();
        }

        List<TopicCount> topicCounts = new ArrayList<>();
        for (JsonNode item : topicPreferenceNode) {
            String topic = JsonHelper.getText(item, "topic");
            Integer count = JsonHelper.getInt(item, "count");
            if (StringUtils.hasText(topic) && count != null) {
                topicCounts.add(new TopicCount(topic, count));
            }
        }
        return topicCounts.stream()
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .limit(limit)
                .map(TopicCount::topic)
                .toList();
    }

    private Integer parseTopType(String behaviorJson) {
        if (!StringUtils.hasText(behaviorJson)) {
            return null;
        }
        JsonNode behaviorNode = JsonHelper.readTree(behaviorJson);
        JsonNode typePreferenceNode = JsonHelper.get(behaviorNode, "typePreference");
        if (typePreferenceNode == null || !typePreferenceNode.isArray()) {
            return null;
        }

        Integer type = null;
        int maxCount = -1;
        for (JsonNode item : typePreferenceNode) {
            Integer currentType = JsonHelper.getInt(item, "type");
            Integer currentCount = JsonHelper.getInt(item, "count");
            if (currentType != null && currentCount != null && currentCount > maxCount) {
                maxCount = currentCount;
                type = currentType;
            }
        }
        return type;
    }

    private String toStringArrayJson(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(this::quote)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String defaultArrayJson(String json) {
        return StringUtils.hasText(json) ? json : "[]";
    }

    private record TopicCount(String topic, int count) {
    }
}
