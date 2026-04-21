package athena.insight.biz.service.impl;

import athena.insight.biz.domain.dataobject.NoteFeatureDO;
import athena.insight.biz.domain.dataobject.NoteTopicRelationDO;
import athena.insight.biz.domain.dataobject.TopicDO;
import athena.insight.biz.domain.mapper.NoteFeatureMapper;
import athena.insight.biz.domain.mapper.NoteTopicRelationMapper;
import athena.insight.biz.rpc.GroundFeignApi;
import athena.insight.biz.service.NoteFeatureService;
import athena.insight.biz.service.TopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NoteFeatureServiceImpl implements NoteFeatureService {

    private static final byte STATUS_ACTIVE = 1;

    @Resource
    private NoteFeatureMapper noteFeatureMapper;

    @Resource
    private NoteTopicRelationMapper noteTopicRelationMapper;

    @Resource
    private GroundFeignApi groundFeignApi;

    @Resource
    private TopicService topicService;

    @Override
    public NoteFeatureDO getByNoteId(Long noteId) {
        if (noteId == null) {
            return null;
        }
        LambdaQueryWrapper<NoteFeatureDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NoteFeatureDO::getNoteId, noteId).last("limit 1");
        return noteFeatureMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteFeatureDO refreshByNoteId(Long noteId) {
        if (noteId == null) {
            return null;
        }
        Map<String, Object> base = findBlogBase(noteId);
        if (base == null || base.isEmpty()) {
            log.warn("[NoteFeature] 刷新失败，未找到内容基础信息, noteId={}", noteId);
            return null;
        }

        Byte type = getByte(base.get("type"));
        if (type == null) {
            log.warn("[NoteFeature] 刷新失败，内容类型为空, noteId={}", noteId);
            return null;
        }

        Map<String, Object> detail = groundFeignApi.getBlogDetail(noteId, type);
        if (detail == null || detail.isEmpty()) {
            detail = base;
        }

        NoteFeatureDO target = getByNoteId(noteId);
        if (target == null) {
            target = new NoteFeatureDO();
            target.setNoteId(noteId);
            target.setFeatureVersion(1);
        }

        target.setType(type);
        target.setAuthorId(getLong(detail.get("userId"), getLong(base.get("userId"), getNestedUserId(base.get("userDTO")))));
        target.setTitle(getString(detail.get("title"), getString(base.get("title"), null)));
        target.setCoverUrl(getString(detail.get("coverUrl"), getString(base.get("coverUrl"), null)));
        target.setChannelId(getInteger(detail.get("channelId"), getInteger(base.get("channelId"), null)));
        target.setStatus(getByte(detail.get("status"), getByte(base.get("status"), STATUS_ACTIVE)));
        target.setTopicFeatureJson(buildTopicFeatureJson(noteId));
        target.setHotScore(buildHotScore(detail, base));
        target.setQualityScore(buildQualityScore(detail, base));
        if (target.getFeatureVersion() == null) {
            target.setFeatureVersion(1);
        }

        if (target.getId() == null) {
            noteFeatureMapper.insert(target);
        } else {
            noteFeatureMapper.updateById(target);
        }
        log.info("[NoteFeature] 刷新成功, noteId={}, type={}, hotScore={}, qualityScore={}",
                noteId, target.getType(), target.getHotScore(), target.getQualityScore());
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByNoteId(Long noteId) {
        if (noteId == null) {
            return;
        }
        noteFeatureMapper.delete(new LambdaQueryWrapper<NoteFeatureDO>().eq(NoteFeatureDO::getNoteId, noteId));
        noteTopicRelationMapper.delete(new LambdaQueryWrapper<NoteTopicRelationDO>().eq(NoteTopicRelationDO::getNoteId, noteId));
        log.info("[NoteFeature] 删除内容特征成功, noteId={}", noteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NoteFeatureDO> refreshPublicPool(Integer pageNum, Integer pageSize) {
        int actualPageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        int actualPageSize = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        List<Map<String, Object>> blogs = groundFeignApi.getBlogListPage(actualPageNum, actualPageSize);
        if (blogs == null || blogs.isEmpty()) {
            return Collections.emptyList();
        }
        return blogs.stream()
                .map(item -> getLong(item.get("blogId"), null))
                .filter(java.util.Objects::nonNull)
                .map(this::refreshByNoteId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Map<String, Object> findBlogBase(Long noteId) {
        for (int page = 1; page <= 20; page++) {
            List<Map<String, Object>> blogs = groundFeignApi.getBlogListPage(page, 50);
            if (blogs == null || blogs.isEmpty()) {
                break;
            }
            for (Map<String, Object> blog : blogs) {
                Long blogId = getLong(blog.get("blogId"), null);
                if (noteId.equals(blogId)) {
                    return blog;
                }
            }
            if (blogs.size() < 50) {
                break;
            }
        }
        return null;
    }

    private String buildTopicFeatureJson(Long noteId) {
        List<TopicDO> topics = topicService.listTopicsByNoteId(noteId);
        if (topics == null || topics.isEmpty()) {
            return "[]";
        }
        List<String> names = topics.stream()
                .map(TopicDO::getTopicName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return names.stream().map(this::quote).collect(Collectors.joining(",", "[", "]"));
    }

    private BigDecimal buildHotScore(Map<String, Object> detail, Map<String, Object> base) {
        long likeTotal = getLong(detail.get("likeTotal"), getLong(base.get("likeTotal"), 0L));
        long collectTotal = getLong(detail.get("collectTotal"), 0L);
        long commentTotal = getLong(detail.get("commentTotal"), 0L);
        double raw = likeTotal * 0.5 + collectTotal * 0.3 + commentTotal * 0.2;
        return BigDecimal.valueOf(Math.min(10D, raw <= 0 ? 0D : Math.log1p(raw) * 3D)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal buildQualityScore(Map<String, Object> detail, Map<String, Object> base) {
        double score = 6.0D;
        String title = getString(detail.get("title"), getString(base.get("title"), null));
        String coverUrl = getString(detail.get("coverUrl"), getString(base.get("coverUrl"), null));
        String content = getString(detail.get("content"), null);
        Integer channelId = getInteger(detail.get("channelId"), getInteger(base.get("channelId"), null));
        Byte status = getByte(detail.get("status"), getByte(base.get("status"), STATUS_ACTIVE));
        List<TopicDO> topics = topicService.listTopicsByNoteId(getLong(base.get("blogId"), getLong(detail.get("id"), null)));

        if (StringUtils.hasText(title) && title.length() >= 8) {
            score += 1.0D;
        }
        if (StringUtils.hasText(coverUrl)) {
            score += 0.8D;
        }
        if (StringUtils.hasText(content) && content.length() >= 40) {
            score += 0.8D;
        }
        if (channelId != null) {
            score += 0.4D;
        }
        if (status != null && status == STATUS_ACTIVE) {
            score += 0.5D;
        }
        if (topics != null && !topics.isEmpty()) {
            score += Math.min(0.5D, topics.size() * 0.2D);
        }
        return BigDecimal.valueOf(Math.min(10D, score)).setScale(4, RoundingMode.HALF_UP);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Long getNestedUserId(Object userDTO) {
        if (!(userDTO instanceof Map<?, ?> map)) {
            return null;
        }
        Object[] candidates = new Object[]{map.get("userId"), map.get("id")};
        for (Object candidate : candidates) {
            Long value = getLong(candidate, null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String getString(Object primary, String fallback) {
        if (primary == null) {
            return fallback;
        }
        String value = String.valueOf(primary);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Long getLong(Object primary, Long fallback) {
        try {
            if (primary instanceof Number n) {
                return n.longValue();
            }
            if (primary instanceof String s && StringUtils.hasText(s)) {
                return Long.parseLong(s);
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private Integer getInteger(Object primary, Integer fallback) {
        try {
            if (primary instanceof Number n) {
                return n.intValue();
            }
            if (primary instanceof String s && StringUtils.hasText(s)) {
                return Integer.parseInt(s);
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private Byte getByte(Object primary) {
        return getByte(primary, null);
    }

    private Byte getByte(Object primary, Byte fallback) {
        try {
            if (primary instanceof Number n) {
                return n.byteValue();
            }
            if (primary instanceof String s && StringUtils.hasText(s)) {
                return Byte.parseByte(s);
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }
}
