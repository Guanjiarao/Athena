package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.ground.biz.constant.ViewRecordConstants;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.mq.producer.ViewRecordProducer;
import athena.ground.biz.rpc.UserAuthFeginApi;
import athena.ground.biz.service.ViewRecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 浏览记录 Service 实现
 */
@Slf4j
@Service
public class ViewRecordServiceImpl implements ViewRecordService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ViewRecordProducer viewRecordProducer;

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserAuthFeginApi userAuthFeginApi;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Result recordView(Long userId, Long noteId, Integer duration) {
        try {
            String key = ViewRecordConstants.VIEW_RECENT_KEY_PREFIX + userId;
            long now = System.currentTimeMillis();

            // 1. 写入 Redis ZSet（score=时间戳，member=noteId）
            log.info("[recordView] 写入Redis ZSet, key={}, noteId={}, score={}", key, noteId, now);
            stringRedisTemplate.opsForZSet().add(key, noteId.toString(), now);

            // 2. 裁剪，只保留最近 200 条（移除排名靠前的旧数据）
            long size = Optional.ofNullable(stringRedisTemplate.opsForZSet().zCard(key)).orElse(0L);
            log.info("[recordView] 当前ZSet大小={}, 上限={}", size, ViewRecordConstants.VIEW_RECENT_MAX_SIZE);
            if (size > ViewRecordConstants.VIEW_RECENT_MAX_SIZE) {
                stringRedisTemplate.opsForZSet().removeRange(key, 0,
                        size - ViewRecordConstants.VIEW_RECENT_MAX_SIZE - 1);
                log.info("[recordView] 裁剪完成, 移除了{}条旧记录", size - ViewRecordConstants.VIEW_RECENT_MAX_SIZE);
            }

            // 3. 发送 MQ 消息异步落库
            String viewTime = LocalDateTime.now().format(FORMATTER);
            log.info("[recordView] 发送MQ消息, userId={}, noteId={}, viewTime={}, duration={}s", userId, noteId, viewTime, duration);
            viewRecordProducer.sendViewMessage(userId, noteId, viewTime, duration);

            return Result.ok();
        } catch (Exception e) {
            log.error("[recordView] 记录浏览失败, userId={}, noteId={}", userId, noteId, e);
            return Result.fail("记录浏览失败");
        }
    }

    @Override
    public Result getRecentViews(Long userId, Long cursor, Integer pageSize) {
        try {
            String key = ViewRecordConstants.VIEW_RECENT_KEY_PREFIX + userId;

            // 游标翻页：首页用 +inf，翻页用 cursor-1 作为 max
            double max = (cursor == null || cursor == 0) ? Double.MAX_VALUE : cursor - 1;
            double min = 0;

            log.info("[getRecentViews] 查询Redis, key={}, max={}, min={}, pageSize={}", key, max, min, pageSize);

            // ZREVRANGEBYSCORE key max min LIMIT 0 pageSize（带 score）
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeByScoreWithScores(key, min, max, 0, pageSize);

            if (tuples == null || tuples.isEmpty()) {
                log.info("[getRecentViews] 无浏览记录, userId={}", userId);
                return Result.ok(Collections.emptyList());
            }

            // 提取 noteId 列表（保持时间倒序）
            List<Long> noteIds = new ArrayList<>();
            long nextCursor = 0;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                noteIds.add(Long.valueOf(tuple.getValue()));
                nextCursor = tuple.getScore().longValue(); // 最后一条的 score 就是下一页的 cursor
            }

            log.info("[getRecentViews] 从Redis获取到{}条记录, noteIds={}, nextCursor={}", noteIds.size(), noteIds, nextCursor);

            // 批量查询笔记摘要，转成 BlogListDTO
            List<BlogListDTO> list = noteIds.stream()
                    .map(noteId -> {
                        NoteBasicDO note = noteBasicMapper.selectByNoteId(noteId);
                        if (note == null) {
                            log.warn("[getRecentViews] 笔记不存在, noteId={}", noteId);
                        }
                        return note;
                    })
                    .filter(Objects::nonNull)
                    .map(this::toBlogListDTO)
                    .collect(Collectors.toList());

            log.info("[getRecentViews] 最终返回{}条BlogListDTO, userId={}", list.size(), userId);

            // 用 Result 的 data 返回列表，total 字段复用为 nextCursor（游标）
            Result<List<BlogListDTO>> result = new Result<>();
            result.setCode(200);
            result.setMessage("成功");
            result.setData(list);
            result.setTotal(nextCursor); // 前端下次请求带上这个值作为 cursor
            return result;
        } catch (Exception e) {
            log.error("[getRecentViews] 查询最近浏览失败, userId={}", userId, e);
            return Result.fail("查询最近浏览失败");
        }
    }

    /**
     * NoteBasicDO → BlogListDTO（复用现有项目的转换逻辑）
     */
    private BlogListDTO toBlogListDTO(NoteBasicDO noteBasicDO) {
        BlogListDTO dto = new BlogListDTO();
        BeanUtils.copyProperties(noteBasicDO, dto);
        dto.setBlogId(noteBasicDO.getNoteId());

        // 查询作者信息
        try {
            UserDTO userDTO = userAuthFeginApi.findByUserId(noteBasicDO.getUserId());
            dto.setUserDTO(userDTO);
        } catch (Exception e) {
            log.warn("查询用户信息失败, userId={}", noteBasicDO.getUserId());
        }

        // 查询点赞数
        try {
            NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteBasicDO.getNoteId());
            dto.setLikeTotal(noteCountDO != null ? noteCountDO.getLikeTotal() : 0L);
        } catch (Exception e) {
            dto.setLikeTotal(0L);
        }

        return dto;
    }
}
