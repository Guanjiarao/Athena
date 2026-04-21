package athena.insight.biz.service.impl;

import athena.insight.biz.domain.dataobject.NoteFeatureDO;
import athena.insight.biz.domain.dataobject.TopicDO;
import athena.insight.biz.domain.dto.RecommendQueryDTO;
import athena.insight.biz.domain.mapper.NoteFeatureMapper;
import athena.insight.biz.domain.vo.RecommendItemVO;
import athena.insight.biz.domain.vo.RecommendResultVO;
import athena.insight.biz.domain.vo.UserFeatureSnapshotVO;
import athena.insight.biz.rpc.GroundFeignApi;
import athena.insight.biz.service.RecommendationService;
import athena.insight.biz.service.TopicService;
import athena.insight.biz.service.UserFeatureService;
import athena.insight.biz.util.JsonHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final byte STATUS_ACTIVE = 1;
    private static final int CANDIDATE_LIMIT = 300;
    private static final Pattern LONG_PATTERN = Pattern.compile("\\d+");
    private static final Pattern TYPE_PREFERENCE_PATTERN = Pattern.compile("\\{\\\"type\\\":(\\d+),\\\"count\\\":(\\d+)\\}");
    private static final Pattern TOPIC_PREFERENCE_PATTERN = Pattern.compile("\\{\\\"topic\\\":\\\"(.*?)\\\",\\\"count\\\":(\\d+)\\}");
    private static final Pattern INTEGER_FIELD_PATTERN = Pattern.compile("\"%s\":(null|\\d+)");
    private static final Pattern STRING_ARRAY_PATTERN = Pattern.compile("\"(.*?)\"");

    @Resource
    private NoteFeatureMapper noteFeatureMapper;

    @Resource
    private TopicService topicService;

    @Resource
    private UserFeatureService userFeatureService;

    @Resource
    private GroundFeignApi groundFeignApi;

    @Override
    public RecommendResultVO recommend(Long userId, RecommendQueryDTO request) {
        RecommendResultVO resultVO = new RecommendResultVO();
        if (request == null || request.getType() == null) {
            resultVO.setItems(Collections.emptyList());
            return resultVO;
        }

        Integer pageNum = request.getPageNum() == null || request.getPageNum() <= 0 ? 1 : request.getPageNum();
        Integer pageSize = request.getPageSize() == null || request.getPageSize() <= 0 ? 10 : request.getPageSize();

        UserFeatureSnapshotVO featureSnapshotVO = userId == null ? new UserFeatureSnapshotVO() : userFeatureService.getSnapshot(userId);
        BehaviorFeatures behaviorFeatures = parseBehaviorFeatures(featureSnapshotVO == null ? null : featureSnapshotVO.getBehaviorFeatureJson());
        HealthFeatures healthFeatures = parseHealthFeatures(featureSnapshotVO == null ? null : featureSnapshotVO.getHealthFeatureJson());

        LambdaQueryWrapper<NoteFeatureDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteFeatureDO::getType, request.getType())
                .eq(NoteFeatureDO::getStatus, STATUS_ACTIVE)
                .orderByDesc(NoteFeatureDO::getHotScore)
                .orderByDesc(NoteFeatureDO::getUpdateTime)
                .orderByDesc(NoteFeatureDO::getId)
                .last("limit " + CANDIDATE_LIMIT);

        List<NoteFeatureDO> noteFeatureList = noteFeatureMapper.selectList(queryWrapper);
        if (noteFeatureList == null) {
            noteFeatureList = Collections.emptyList();
        }

        Map<Long, RecallCandidate> recallMap = new LinkedHashMap<>();
        recallByTopicPreference(noteFeatureList, behaviorFeatures, recallMap);
        recallByHealthTopics(noteFeatureList, healthFeatures, recallMap);
        recallByHot(noteFeatureList, recallMap);
        recallByFresh(noteFeatureList, recallMap);

        List<ScoredRecommendItem> scoredItems = recallMap.values().stream()
                .map(RecallCandidate::noteFeature)
                .filter(item -> !shouldFilter(item, userId, behaviorFeatures))
                .map(item -> toScoredRecommendItem(item, request.getType(), behaviorFeatures, healthFeatures, recallMap.get(item.getNoteId())))
                .sorted(Comparator.comparing(ScoredRecommendItem::score).reversed()
                        .thenComparing(ScoredRecommendItem::noteId, Comparator.reverseOrder()))
                .toList();

        List<ScoredRecommendItem> diversified = diversify(scoredItems);
        List<RecommendItemVO> mergedItems = mergeWithFallback(diversified, request, userId, pageNum, pageSize, behaviorFeatures);

        resultVO.setType(request.getType());
        resultVO.setPageNum(pageNum);
        resultVO.setPageSize(pageSize);
        resultVO.setItems(mergedItems);
        return resultVO;
    }

    private boolean shouldFilter(NoteFeatureDO noteFeatureDO, Long userId, BehaviorFeatures behaviorFeatures) {
        if (noteFeatureDO == null) {
            return true;
        }
        if (userId != null && userId.equals(noteFeatureDO.getAuthorId())) {
            return true;
        }
        return behaviorFeatures.recentViewedNoteIds.contains(noteFeatureDO.getNoteId());
    }

    private ScoredRecommendItem toScoredRecommendItem(NoteFeatureDO noteFeatureDO,
                                                      Byte requestType,
                                                      BehaviorFeatures behaviorFeatures,
                                                      HealthFeatures healthFeatures,
                                                      RecallCandidate recallCandidate) {
        List<String> topics = buildTopics(noteFeatureDO);
        double hotScore = normalizedScore(noteFeatureDO.getHotScore(), 10D);
        double qualityScore = normalizedScore(noteFeatureDO.getQualityScore(), 10D);
        double freshnessScore = buildFreshnessScore(noteFeatureDO);
        double typeMatchScore = buildTypeMatchScore(requestType, behaviorFeatures.typePreference);
        double topicMatchScore = buildTopicMatchScore(topics, behaviorFeatures.topicPreference);
        double healthMatchScore = buildHealthMatchScore(requestType, topics, healthFeatures);
        double recallBoost = buildRecallBoost(recallCandidate);
        double finalScore = buildFinalScore(requestType, hotScore, qualityScore, freshnessScore, typeMatchScore, topicMatchScore, healthMatchScore, recallBoost);

        RecommendItemVO itemVO = new RecommendItemVO();
        itemVO.setNoteId(noteFeatureDO.getNoteId());
        itemVO.setType(noteFeatureDO.getType());
        itemVO.setTitle(noteFeatureDO.getTitle());
        itemVO.setCoverUrl(noteFeatureDO.getCoverUrl());
        itemVO.setAuthorId(noteFeatureDO.getAuthorId());
        itemVO.setTopics(topics);
        itemVO.setReason(buildReason(noteFeatureDO.getType(), topics, topicMatchScore, healthMatchScore, typeMatchScore, healthFeatures, recallCandidate));
        itemVO.setScore(round(finalScore));
        return new ScoredRecommendItem(itemVO, finalScore, noteFeatureDO.getNoteId());
    }

    private List<String> buildTopics(NoteFeatureDO noteFeatureDO) {
        if (noteFeatureDO == null) {
            return Collections.emptyList();
        }
        List<String> featureTopics = JsonHelper.toStringList(JsonHelper.readTree(noteFeatureDO.getTopicFeatureJson()));
        if (featureTopics != null && !featureTopics.isEmpty()) {
            return featureTopics.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        Long noteId = noteFeatureDO.getNoteId();
        if (noteId == null) {
            return Collections.emptyList();
        }
        List<TopicDO> topicList = topicService.listTopicsByNoteId(noteId);
        if (topicList == null || topicList.isEmpty()) {
            return Collections.emptyList();
        }
        return topicList.stream()
                .map(TopicDO::getTopicName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<RecommendItemVO> mergeWithFallback(List<ScoredRecommendItem> diversified,
                                                    RecommendQueryDTO request,
                                                    Long userId,
                                                    Integer pageNum,
                                                    Integer pageSize,
                                                    BehaviorFeatures behaviorFeatures) {
        int fromIndex = Math.min((pageNum - 1) * pageSize, diversified.size());
        int toIndex = Math.min(fromIndex + pageSize, diversified.size());
        List<RecommendItemVO> items = new ArrayList<>(diversified.subList(fromIndex, toIndex).stream()
                .map(ScoredRecommendItem::item)
                .collect(Collectors.toList()));
        if (items.size() >= pageSize) {
            return items;
        }

        LinkedHashMap<Long, RecommendItemVO> merged = new LinkedHashMap<>();
        items.forEach(item -> merged.put(item.getNoteId(), item));
        int needCount = pageSize - items.size();
        List<RecommendItemVO> fallbackItems = loadFallbackItems(request, userId, behaviorFeatures, needCount, merged.keySet());
        fallbackItems.forEach(item -> merged.putIfAbsent(item.getNoteId(), item));

        return merged.values().stream()
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    private List<RecommendItemVO> loadFallbackItems(RecommendQueryDTO request,
                                                    Long userId,
                                                    BehaviorFeatures behaviorFeatures,
                                                    int needCount,
                                                    Set<Long> existedNoteIds) {
        if (needCount <= 0 || request == null || request.getType() == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> sourceItems = fetchFallbackSource(request.getType(), request.getChannelId(), needCount * 3);
        if (sourceItems == null || sourceItems.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> excludeIds = new LinkedHashSet<>(existedNoteIds == null ? Collections.emptySet() : existedNoteIds);
        return sourceItems.stream()
                .map(item -> toFallbackItem(item, request.getType()))
                .filter(item -> item != null && item.getNoteId() != null)
                .filter(item -> !excludeIds.contains(item.getNoteId()))
                .filter(item -> userId == null || !userId.equals(item.getAuthorId()))
                .filter(item -> !behaviorFeatures.recentViewedNoteIds.contains(item.getNoteId()))
                .peek(item -> excludeIds.add(item.getNoteId()))
                .limit(needCount)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> fetchFallbackSource(Byte requestType, Integer channelId, int pageSize) {
        int actualPageSize = Math.max(pageSize, 10);
        if (requestType != null) {
            return groundFeignApi.getBlogListByType((int) requestType, 1, actualPageSize);
        }
        return groundFeignApi.getBlogListPage(1, actualPageSize);
    }

    private RecommendItemVO toFallbackItem(Map<String, Object> source, Byte requestType) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Long noteId = getLong(source.get("blogId"), getLong(source.get("noteId"), null));
        if (noteId == null) {
            return null;
        }
        RecommendItemVO itemVO = new RecommendItemVO();
        itemVO.setNoteId(noteId);
        itemVO.setType(getByte(source.get("type"), requestType));
        itemVO.setTitle(getString(source.get("title"), null));
        itemVO.setCoverUrl(getString(source.get("coverUrl"), null));
        itemVO.setAuthorId(getLong(source.get("userId"), getNestedUserId(source.get("userDTO"))));
        itemVO.setTopics(Collections.emptyList());
        itemVO.setReason(buildFallbackReason(requestType, getString(source.get("channelName"), null)));
        itemVO.setScore(0D);
        return itemVO;
    }

    private String buildFallbackReason(Byte requestType, String channelName) {
        if (requestType != null && requestType == 0 && StringUtils.hasText(channelName)) {
            return "为你补充“" + channelName + "”频道内容";
        }
        if (requestType == null) {
            return "为你补充广场内容";
        }
        return switch (requestType) {
            case 0 -> "为你补充频道科普内容";
            case 1 -> "为你补充广场图文内容";
            case 2 -> "为你补充广场视频内容";
            default -> "为你补充广场内容";
        };
    }

    private Long getNestedUserId(Object userDTO) {
        if (!(userDTO instanceof Map<?, ?> map)) {
            return null;
        }
        Long userId = getLong(map.get("userId"), null);
        if (userId != null) {
            return userId;
        }
        return getLong(map.get("id"), null);
    }

    private Long getLong(Object primary, Long fallback) {
        try {
            if (primary instanceof Number number) {
                return number.longValue();
            }
            if (primary instanceof String value && StringUtils.hasText(value)) {
                return Long.parseLong(value);
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private Byte getByte(Object primary, Byte fallback) {
        try {
            if (primary instanceof Number number) {
                return number.byteValue();
            }
            if (primary instanceof String value && StringUtils.hasText(value)) {
                return Byte.parseByte(value);
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private String getString(Object primary, String fallback) {
        if (primary == null) {
            return fallback;
        }
        String value = String.valueOf(primary);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BehaviorFeatures parseBehaviorFeatures(String behaviorFeatureJson) {
        if (!StringUtils.hasText(behaviorFeatureJson)) {
            return new BehaviorFeatures();
        }

        BehaviorFeatures features = new BehaviorFeatures();
        features.recentViewedNoteIds = parseRecentViewedNoteIds(behaviorFeatureJson);
        features.typePreference = parseTypePreference(behaviorFeatureJson);
        features.topicPreference = parseTopicPreference(behaviorFeatureJson);
        return features;
    }

    private HealthFeatures parseHealthFeatures(String healthFeatureJson) {
        if (!StringUtils.hasText(healthFeatureJson)) {
            return new HealthFeatures();
        }
        HealthFeatures features = new HealthFeatures();
        features.currentModeType = parseIntegerField(healthFeatureJson, "currentModeType");
        features.symptomTopics = parseStringArray(extractArrayJson(healthFeatureJson, "symptomTopics"));
        return features;
    }

    private Set<Long> parseRecentViewedNoteIds(String behaviorFeatureJson) {
        Set<Long> values = new HashSet<>();
        String marker = "\"recentViewedNoteIds\":";
        int start = behaviorFeatureJson.indexOf(marker);
        if (start < 0) {
            return values;
        }
        int left = behaviorFeatureJson.indexOf('[', start);
        int right = behaviorFeatureJson.indexOf(']', left);
        if (left < 0 || right < 0 || right <= left) {
            return values;
        }
        String content = behaviorFeatureJson.substring(left + 1, right);
        Matcher matcher = LONG_PATTERN.matcher(content);
        while (matcher.find()) {
            values.add(Long.parseLong(matcher.group()));
        }
        return values;
    }

    private Map<Integer, Long> parseTypePreference(String behaviorFeatureJson) {
        Map<Integer, Long> values = new HashMap<>();
        Matcher matcher = TYPE_PREFERENCE_PATTERN.matcher(behaviorFeatureJson);
        while (matcher.find()) {
            values.put(Integer.parseInt(matcher.group(1)), Long.parseLong(matcher.group(2)));
        }
        return values;
    }

    private Map<String, Long> parseTopicPreference(String behaviorFeatureJson) {
        Map<String, Long> values = new HashMap<>();
        Matcher matcher = TOPIC_PREFERENCE_PATTERN.matcher(behaviorFeatureJson);
        while (matcher.find()) {
            values.put(matcher.group(1), Long.parseLong(matcher.group(2)));
        }
        return values;
    }

    private double buildTypeMatchScore(Byte requestType, Map<Integer, Long> typePreference) {
        if (requestType == null || typePreference.isEmpty()) {
            return 0D;
        }
        long targetCount = typePreference.getOrDefault((int) requestType, 0L);
        long maxCount = typePreference.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (maxCount <= 0) {
            return 0D;
        }
        return (double) targetCount / maxCount;
    }

    private double buildTopicMatchScore(List<String> topics, Map<String, Long> topicPreference) {
        if (topics == null || topics.isEmpty() || topicPreference.isEmpty()) {
            return 0D;
        }
        long sum = topics.stream()
                .mapToLong(topic -> topicPreference.getOrDefault(topic, 0L))
                .sum();
        long maxCount = topicPreference.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (maxCount <= 0) {
            return 0D;
        }
        return Math.min(1D, (double) sum / (maxCount * Math.max(1, topics.size())));
    }

    private double buildHealthMatchScore(Byte requestType, List<String> topics, HealthFeatures healthFeatures) {
        if (healthFeatures == null) {
            return 0D;
        }
        double topicScore = 0D;
        if (topics != null && !topics.isEmpty() && !healthFeatures.symptomTopics.isEmpty()) {
            long matchCount = topics.stream().filter(healthFeatures.symptomTopics::contains).count();
            topicScore = (double) matchCount / Math.max(1, healthFeatures.symptomTopics.size());
        }
        double modeScore = 0D;
        if (healthFeatures.currentModeType != null && requestType != null) {
            if (healthFeatures.currentModeType == 0 && requestType == 0) {
                modeScore = 0.3D;
            } else if ((healthFeatures.currentModeType == 1 || healthFeatures.currentModeType == 2) && requestType == 0) {
                modeScore = 0.2D;
            }
        }
        return Math.min(1D, topicScore + modeScore);
    }

    private void recallByTopicPreference(List<NoteFeatureDO> candidates, BehaviorFeatures behaviorFeatures, Map<Long, RecallCandidate> recallMap) {
        if (behaviorFeatures.topicPreference.isEmpty()) {
            return;
        }
        Set<String> topTopics = behaviorFeatures.topicPreference.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (NoteFeatureDO candidate : candidates) {
            List<String> topics = buildTopics(candidate);
            if (topics.stream().anyMatch(topTopics::contains)) {
                recallMap.computeIfAbsent(candidate.getNoteId(), id -> new RecallCandidate(candidate)).sources.add("topic");
            }
        }
    }

    private void recallByHealthTopics(List<NoteFeatureDO> candidates, HealthFeatures healthFeatures, Map<Long, RecallCandidate> recallMap) {
        if (healthFeatures.symptomTopics.isEmpty()) {
            return;
        }
        Set<String> symptomTopics = new LinkedHashSet<>(healthFeatures.symptomTopics.stream().limit(3).toList());
        for (NoteFeatureDO candidate : candidates) {
            List<String> topics = buildTopics(candidate);
            if (topics.stream().anyMatch(symptomTopics::contains)) {
                recallMap.computeIfAbsent(candidate.getNoteId(), id -> new RecallCandidate(candidate)).sources.add("health");
            }
        }
    }

    private void recallByHot(List<NoteFeatureDO> candidates, Map<Long, RecallCandidate> recallMap) {
        candidates.stream()
                .sorted(Comparator.comparing(NoteFeatureDO::getHotScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .limit(80)
                .forEach(candidate -> recallMap.computeIfAbsent(candidate.getNoteId(), id -> new RecallCandidate(candidate)).sources.add("hot"));
    }

    private void recallByFresh(List<NoteFeatureDO> candidates, Map<Long, RecallCandidate> recallMap) {
        candidates.stream()
                .sorted(Comparator.comparing(NoteFeatureDO::getUpdateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(NoteFeatureDO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .forEach(candidate -> recallMap.computeIfAbsent(candidate.getNoteId(), id -> new RecallCandidate(candidate)).sources.add("fresh"));
    }

    private double buildFreshnessScore(NoteFeatureDO noteFeatureDO) {
        if (noteFeatureDO == null || noteFeatureDO.getUpdateTime() == null) {
            return 0D;
        }
        long hours = java.time.Duration.between(noteFeatureDO.getUpdateTime(), java.time.LocalDateTime.now()).toHours();
        if (hours <= 24) return 1D;
        if (hours <= 72) return 0.8D;
        if (hours <= 7 * 24) return 0.6D;
        if (hours <= 30L * 24) return 0.3D;
        return 0.1D;
    }

    private double buildRecallBoost(RecallCandidate recallCandidate) {
        if (recallCandidate == null || recallCandidate.sources.isEmpty()) {
            return 0D;
        }
        double boost = 0D;
        if (recallCandidate.sources.contains("health")) boost += 0.08D;
        if (recallCandidate.sources.contains("topic")) boost += 0.06D;
        if (recallCandidate.sources.contains("fresh")) boost += 0.03D;
        if (recallCandidate.sources.contains("hot")) boost += 0.02D;
        return Math.min(0.15D, boost);
    }

    private double buildFinalScore(Byte requestType,
                                   double hotScore,
                                   double qualityScore,
                                   double freshnessScore,
                                   double typeMatchScore,
                                   double topicMatchScore,
                                   double healthMatchScore,
                                   double recallBoost) {
        double score;
        if (requestType != null && requestType == 0) {
            score = 0.30 * healthMatchScore + 0.22 * topicMatchScore + 0.10 * typeMatchScore + 0.15 * hotScore + 0.13 * freshnessScore + 0.10 * qualityScore;
        } else if (requestType != null && requestType == 1) {
            score = 0.28 * topicMatchScore + 0.18 * typeMatchScore + 0.22 * hotScore + 0.17 * freshnessScore + 0.10 * qualityScore + 0.05 * healthMatchScore;
        } else {
            score = 0.24 * topicMatchScore + 0.22 * typeMatchScore + 0.22 * hotScore + 0.17 * freshnessScore + 0.10 * qualityScore + 0.05 * healthMatchScore;
        }
        return Math.min(1D, score + recallBoost);
    }

    private List<ScoredRecommendItem> diversify(List<ScoredRecommendItem> scoredItems) {
        if (scoredItems.size() <= 2) {
            return scoredItems;
        }
        List<ScoredRecommendItem> remaining = new ArrayList<>(scoredItems);
        List<ScoredRecommendItem> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ScoredRecommendItem selected = remaining.remove(0);
            if (!result.isEmpty() && sameAuthorOrFirstTopic(result.get(result.size() - 1).item(), selected.item())) {
                int alternativeIndex = findAlternativeIndex(remaining, result.get(result.size() - 1).item());
                if (alternativeIndex >= 0) {
                    remaining.add(0, selected);
                    selected = remaining.remove(alternativeIndex);
                }
            }
            result.add(selected);
        }
        return result;
    }

    private int findAlternativeIndex(List<ScoredRecommendItem> remaining, RecommendItemVO previous) {
        for (int i = 0; i < remaining.size(); i++) {
            if (!sameAuthorOrFirstTopic(previous, remaining.get(i).item())) {
                return i;
            }
        }
        return -1;
    }

    private boolean sameAuthorOrFirstTopic(RecommendItemVO previous, RecommendItemVO current) {
        if (previous == null || current == null) {
            return false;
        }
        if (previous.getAuthorId() != null && previous.getAuthorId().equals(current.getAuthorId())) {
            return true;
        }
        String previousTopic = firstTopic(previous.getTopics());
        String currentTopic = firstTopic(current.getTopics());
        return StringUtils.hasText(previousTopic) && previousTopic.equals(currentTopic);
    }

    private String firstTopic(List<String> topics) {
        return topics == null || topics.isEmpty() ? null : topics.get(0);
    }

    private String buildReason(Byte type,
                               List<String> topics,
                               double topicMatchScore,
                               double healthMatchScore,
                               double typeMatchScore,
                               HealthFeatures healthFeatures,
                               RecallCandidate recallCandidate) {
        if (recallCandidate != null && recallCandidate.sources.contains("health") && healthFeatures != null && !healthFeatures.symptomTopics.isEmpty()) {
            return "结合你近期健康记录关注的“" + healthFeatures.symptomTopics.get(0) + "”主题为你推荐";
        }
        if (recallCandidate != null && recallCandidate.sources.contains("topic") && topics != null && !topics.isEmpty()) {
            return "结合你近期关注的“" + topics.get(0) + "”主题为你推荐";
        }
        if (recallCandidate != null && recallCandidate.sources.contains("fresh") && topics != null && !topics.isEmpty()) {
            return "为你补充近期新发布的“" + topics.get(0) + "”内容";
        }
        if (typeMatchScore > 0.3) {
            return switch (type) {
                case 0 -> "根据你近期偏好的科普内容为你推荐";
                case 1 -> "根据你近期偏好的图文内容为你推荐";
                case 2 -> "根据你近期偏好的视频内容为你推荐";
                default -> "根据你的内容偏好为你推荐";
            };
        }
        if (topicMatchScore > 0.3 && topics != null && !topics.isEmpty()) {
            return "根据你关注的“" + topics.get(0) + "”主题为你推荐";
        }
        if (healthMatchScore > 0.3 && healthFeatures != null && !healthFeatures.symptomTopics.isEmpty()) {
            return "结合你当前状态更适合阅读此内容";
        }
        return switch (type) {
            case 0 -> "根据当前科普热度为你推荐";
            case 1 -> "根据当前广场图文热度为你推荐";
            case 2 -> "根据当前广场视频热度为你推荐";
            default -> "为你推荐";
        };
    }

    private Integer parseIntegerField(String json, String fieldName) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(fieldName)) {
            return null;
        }
        Pattern pattern = Pattern.compile(String.format(INTEGER_FIELD_PATTERN.pattern(), fieldName));
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        if (!StringUtils.hasText(value) || "null".equals(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String extractArrayJson(String json, String fieldName) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(fieldName)) {
            return "[]";
        }
        String marker = "\"" + fieldName + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "[]";
        }
        int left = json.indexOf('[', start);
        int right = json.indexOf(']', left);
        if (left < 0 || right < 0 || right <= left) {
            return "[]";
        }
        return json.substring(left, right + 1);
    }

    private List<String> parseStringArray(String jsonArray) {
        if (!StringUtils.hasText(jsonArray)) {
            return Collections.emptyList();
        }
        Matcher matcher = STRING_ARRAY_PATTERN.matcher(jsonArray);
        List<String> values = new java.util.ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private double normalizedScore(BigDecimal source, double denominator) {
        if (source == null || denominator <= 0) {
            return 0D;
        }
        return Math.min(1D, source.doubleValue() / denominator);
    }

    private Double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static class BehaviorFeatures {
        private Set<Long> recentViewedNoteIds = new HashSet<>();
        private Map<Integer, Long> typePreference = new HashMap<>();
        private Map<String, Long> topicPreference = new HashMap<>();
    }

    private static class HealthFeatures {
        private Integer currentModeType;
        private List<String> symptomTopics = Collections.emptyList();
    }

    private static class RecallCandidate {
        private final NoteFeatureDO noteFeature;
        private final Set<String> sources = new LinkedHashSet<>();

        private RecallCandidate(NoteFeatureDO noteFeature) {
            this.noteFeature = noteFeature;
        }

        private NoteFeatureDO noteFeature() {
            return noteFeature;
        }
    }

    private record ScoredRecommendItem(RecommendItemVO item, double score, Long noteId) {
    }
}
