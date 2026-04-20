package athena.insight.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.insight.biz.domain.dataobject.TopicDO;
import athena.insight.biz.domain.dataobject.UserFeatureSnapshotDO;
import athena.insight.biz.domain.dto.FeatureRefreshDTO;
import athena.insight.biz.domain.mapper.UserFeatureSnapshotMapper;
import athena.insight.biz.domain.vo.UserFeatureSnapshotVO;
import athena.insight.biz.rpc.GroundRpc;
import athena.insight.biz.rpc.RecordRpc;
import athena.insight.biz.rpc.UserAuthRpc;
import athena.insight.biz.service.TopicService;
import athena.insight.biz.service.UserFeatureService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserFeatureServiceImpl implements UserFeatureService {
    @Resource private UserFeatureSnapshotMapper userFeatureSnapshotMapper;
    @Resource private UserAuthRpc userAuthRpc;
    @Resource private GroundRpc groundRpc;
    @Resource private RecordRpc recordRpc;
    @Resource private TopicService topicService;

    @Override
    public UserFeatureSnapshotVO getSnapshot(Long userId) {
        if (userId == null) return new UserFeatureSnapshotVO();
        return toVO(findByUserId(userId));
    }

    @Override
    public UserFeatureSnapshotVO refreshSnapshot(FeatureRefreshDTO request) {
        if (request == null || request.getUserId() == null) return new UserFeatureSnapshotVO();
        Long userId = request.getUserId();
        UserFeatureSnapshotDO snapshotDO = findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        UserDTO userDTO = userAuthRpc.findByUserId(userId);
        List<Map<String, Object>> userBlogs = groundRpc.getBlogListByUserId(userId, 1, 50);
        List<Map<String, Object>> likedBlogs = groundRpc.likeList();
        List<Map<String, Object>> collectedBlogs = groundRpc.collectList();
        List<Map<String, Object>> viewedBlogs = groundRpc.viewHistory(null, 50);
        Map<String, Object> cycleStats = recordRpc.getCycleStats(userId);
        Map<String, Object> prediction = recordRpc.getPrediction(userId);
        List<Map<String, Object>> recentRecords = recordRpc.getRecords(userId, LocalDate.now().minusDays(30).toString(), LocalDate.now().toString());
        String baseFeatureJson = buildBaseFeatureJson(userId, userDTO);
        String behaviorFeatureJson = buildBehaviorFeatureJson(userId, userBlogs, likedBlogs, collectedBlogs, viewedBlogs);
        String healthFeatureJson = buildHealthFeatureJson(userId, cycleStats, prediction, recentRecords);
        if (snapshotDO == null) {
            snapshotDO = new UserFeatureSnapshotDO();
            snapshotDO.setUserId(userId);
            snapshotDO.setFeatureVersion(1);
        }
        snapshotDO.setBaseFeatureJson(defaultJson(baseFeatureJson));
        snapshotDO.setBehaviorFeatureJson(defaultJson(behaviorFeatureJson));
        snapshotDO.setHealthFeatureJson(defaultJson(healthFeatureJson));
        snapshotDO.setGeneratedAt(now);
        if (snapshotDO.getId() == null) {
            userFeatureSnapshotMapper.insert(snapshotDO);
        } else {
            userFeatureSnapshotMapper.updateById(snapshotDO);
        }
        return toVO(snapshotDO);
    }

    private UserFeatureSnapshotDO findByUserId(Long userId) {
        LambdaQueryWrapper<UserFeatureSnapshotDO> q = new LambdaQueryWrapper<>();
        q.eq(UserFeatureSnapshotDO::getUserId, userId).last("limit 1");
        return userFeatureSnapshotMapper.selectOne(q);
    }

    private UserFeatureSnapshotVO toVO(UserFeatureSnapshotDO snapshotDO) {
        UserFeatureSnapshotVO vo = new UserFeatureSnapshotVO();
        if (snapshotDO == null) return vo;
        vo.setUserId(snapshotDO.getUserId());
        vo.setBaseFeatureJson(snapshotDO.getBaseFeatureJson());
        vo.setBehaviorFeatureJson(snapshotDO.getBehaviorFeatureJson());
        vo.setHealthFeatureJson(snapshotDO.getHealthFeatureJson());
        vo.setGeneratedAt(snapshotDO.getGeneratedAt() == null ? null : snapshotDO.getGeneratedAt().toString());
        return vo;
    }

    private String buildBaseFeatureJson(Long userId, UserDTO userDTO) {
        String nickName = userDTO == null ? "" : safe(userDTO.getNickName());
        String icon = userDTO == null ? "" : safe(userDTO.getIcon());
        boolean priority = userDTO != null && Boolean.TRUE.equals(userDTO.getPriority());
        return "{" + "\"userId\":" + userId + "," + "\"nickName\":\"" + nickName + "\"," + "\"icon\":\"" + icon + "\"," + "\"priority\":" + priority + "}";
    }

    private String buildBehaviorFeatureJson(Long userId, List<Map<String, Object>> userBlogs, List<Map<String, Object>> likedBlogs, List<Map<String, Object>> collectedBlogs, List<Map<String, Object>> viewedBlogs) {
        List<Map<String, Object>> publishBlogs = userBlogs == null ? Collections.emptyList() : userBlogs;
        List<Map<String, Object>> likeBlogs = likedBlogs == null ? Collections.emptyList() : likedBlogs;
        List<Map<String, Object>> collectBlogs = collectedBlogs == null ? Collections.emptyList() : collectedBlogs;
        List<Map<String, Object>> viewBlogs = viewedBlogs == null ? Collections.emptyList() : viewedBlogs;
        Map<Integer, Long> typeCounter = new LinkedHashMap<>();
        Map<String, Long> topicCounter = new LinkedHashMap<>();
        Set<Long> recentViewedNoteIds = new LinkedHashSet<>();
        Set<Long> strongPositiveNoteIds = new LinkedHashSet<>();
        accumulateBlogFeatures(publishBlogs, typeCounter, topicCounter, strongPositiveNoteIds, false, recentViewedNoteIds);
        accumulateBlogFeatures(likeBlogs, typeCounter, topicCounter, strongPositiveNoteIds, true, recentViewedNoteIds);
        accumulateBlogFeatures(collectBlogs, typeCounter, topicCounter, strongPositiveNoteIds, true, recentViewedNoteIds);
        accumulateBlogFeatures(viewBlogs, typeCounter, topicCounter, strongPositiveNoteIds, false, recentViewedNoteIds);
        String typePreferenceJson = typeCounter.entrySet().stream().sorted(Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder())).map(e -> "{\"type\":" + e.getKey() + ",\"count\":" + e.getValue() + "}").collect(Collectors.joining(",", "[", "]"));
        String topicPreferenceJson = topicCounter.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).map(e -> "{\"topic\":\"" + safe(e.getKey()) + "\",\"count\":" + e.getValue() + "}").collect(Collectors.joining(",", "[", "]"));
        int activeDays30d = recentViewedNoteIds.size() + publishBlogs.size();
        log.info("[UserFeature] 行为特征聚合完成, userId={}, publishCount={}, likeCount={}, collectCount={}, viewCount={}, topicPreferenceCount={}, topTopics={}, recentViewedCount={}, strongPositiveCount={}",
                userId,
                publishBlogs.size(),
                likeBlogs.size(),
                collectBlogs.size(),
                viewBlogs.size(),
                topicCounter.size(),
                topicCounter.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(3).toList(),
                recentViewedNoteIds.size(),
                strongPositiveNoteIds.size());
        return "{" + "\"viewCount30d\":" + viewBlogs.size() + "," + "\"likeCount30d\":" + likeBlogs.size() + "," + "\"collectCount30d\":" + collectBlogs.size() + "," + "\"activeDays30d\":" + activeDays30d + "," + "\"typePreference\":" + typePreferenceJson + "," + "\"topicPreference\":" + topicPreferenceJson + "," + "\"recentViewedNoteIds\":" + toLongArrayJson(new ArrayList<>(recentViewedNoteIds)) + "," + "\"strongPositiveNoteIds\":" + toLongArrayJson(new ArrayList<>(strongPositiveNoteIds)) + "}";
    }

    private void accumulateBlogFeatures(List<Map<String, Object>> blogs, Map<Integer, Long> typeCounter, Map<String, Long> topicCounter, Set<Long> strongPositiveNoteIds, boolean strongPositive, Set<Long> recentViewedNoteIds) {
        for (Map<String, Object> blog : blogs) {
            Long blogId = getLong(blog.get("blogId"));
            Integer type = getInteger(blog.get("type"));
            Long likeTotal = getLong(blog.get("likeTotal"));
            if (type != null) typeCounter.merge(type, 1L, Long::sum);
            if (blogId != null) {
                recentViewedNoteIds.add(blogId);
                if (strongPositive || (likeTotal != null && likeTotal >= 10)) strongPositiveNoteIds.add(blogId);
                for (TopicDO topicDO : topicService.listTopicsByNoteId(blogId)) {
                    if (topicDO != null && StringUtils.hasText(topicDO.getTopicName())) topicCounter.merge(topicDO.getTopicName(), 1L, Long::sum);
                }
            }
        }
    }

    private String buildHealthFeatureJson(Long userId, Map<String, Object> cycleStats, Map<String, Object> prediction, List<Map<String, Object>> recentRecords) {
        Integer currentModeType = recentRecords == null ? null : recentRecords.stream().map(r -> getInteger(r.get("modeType"))).filter(Objects::nonNull).findFirst().orElse(null);
        Integer averageCycleLength = getInteger(cycleStats == null ? null : cycleStats.get("averageCycleLength"));
        Integer averageDurationDays = getInteger(cycleStats == null ? null : cycleStats.get("averageDurationDays"));
        String predictedNextStartDate = getString(prediction == null ? null : prediction.get("predictedStartDate"));
        String predictedNextEndDate = getString(prediction == null ? null : prediction.get("predictedEndDate"));
        List<String> symptomTopics = parseSymptomTopics(recentRecords);
        long recordDays30d = recentRecords == null ? 0 : recentRecords.stream().map(r -> getString(r.get("recordDate"))).filter(StringUtils::hasText).distinct().count();
        log.info("[UserFeature] 健康特征聚合完成, userId={}, cycleLength={}, durationDays={}, recordCount={}, recordDays30d={}, currentModeType={}, symptomTopicCount={}, symptomTopics={}",
                userId,
                averageCycleLength,
                averageDurationDays,
                recentRecords == null ? 0 : recentRecords.size(),
                recordDays30d,
                currentModeType,
                symptomTopics.size(),
                symptomTopics);
        return "{" + "\"currentModeType\":" + nullableNumber(currentModeType) + "," + "\"averageCycleLength\":" + nullableNumber(averageCycleLength) + "," + "\"averageDurationDays\":" + nullableNumber(averageDurationDays) + "," + "\"todayInActualCycle\":null," + "\"todayInPredictedCycle\":null," + "\"predictedNextStartDate\":" + nullableString(predictedNextStartDate) + "," + "\"predictedNextEndDate\":" + nullableString(predictedNextEndDate) + "," + "\"symptomTopics\":" + toStringArrayJson(symptomTopics) + "," + "\"recordDays30d\":" + recordDays30d + "}";
    }

    private List<String> parseSymptomTopics(List<Map<String, Object>> recentRecords) {
        if (recentRecords == null || recentRecords.isEmpty()) return Collections.emptyList();
        Map<String, Long> topicCounter = new LinkedHashMap<>();
        for (Map<String, Object> record : recentRecords) {
            Integer modeType = getInteger(record.get("modeType"));
            Integer recordItemId = getInteger(record.get("recordItemId"));
            if (modeType == null || recordItemId == null) continue;
            for (TopicDO topicDO : topicService.listTopicsByModeAndRecordItem(modeType.byteValue(), recordItemId)) {
                if (topicDO != null && StringUtils.hasText(topicDO.getTopicName())) topicCounter.merge(topicDO.getTopicName(), 1L, Long::sum);
            }
        }
        return topicCounter.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).map(Map.Entry::getKey).limit(5).collect(Collectors.toList());
    }

    private String toLongArrayJson(List<Long> values) { return values.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(",", "[", "]")); }
    private String toStringArrayJson(List<String> values) { return values.stream().filter(StringUtils::hasText).distinct().map(this::quote).collect(Collectors.joining(",", "[", "]")); }
    private Long getLong(Object value) { try { return value instanceof Number n ? n.longValue() : (value instanceof String s && StringUtils.hasText(s) ? Long.parseLong(s) : null); } catch (Exception e) { return null; } }
    private Integer getInteger(Object value) { try { return value instanceof Number n ? n.intValue() : (value instanceof String s && StringUtils.hasText(s) ? Integer.parseInt(s) : null); } catch (Exception e) { return null; } }
    private String getString(Object value) { return value == null ? null : String.valueOf(value); }
    private String nullableNumber(Integer value) { return value == null ? "null" : String.valueOf(value); }
    private String nullableString(String value) { return StringUtils.hasText(value) ? quote(value) : "null"; }
    private String quote(String value) { return "\"" + safe(value) + "\""; }
    private String defaultJson(String json) { return StringUtils.hasText(json) ? json : "{}"; }
    private String safe(String value) { return !StringUtils.hasText(value) ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
