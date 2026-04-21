package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.ground.biz.constant.NoteInteractionConstants;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteCollectionDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dataobject.NoteLikeDO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteCollectionDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.domain.mapper.NoteLikeDOMapper;
import athena.ground.biz.mq.event.NoteInteractionEvent;
import athena.ground.biz.mq.producer.NoteInteractionProducer;
import athena.ground.biz.rpc.UserAuthFeignApi;
import athena.ground.biz.service.NoteInteractionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 笔记互动服务实现：点赞、收藏、互动计数
 */
@Slf4j
@Service
public class NoteInteractionServiceImpl implements NoteInteractionService {

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;

    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private UserAuthFeignApi userAuthFeignApi;

    @Resource
    private NoteInteractionProducer noteInteractionProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeNote(Long blogId) {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("用户 {} 尝试点赞/取消点赞 blogId={}", userId, blogId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.fail("用户未登录");
            }

            int count = noteLikeDOMapper.countLikeByUserAndNote(userId, blogId);
            log.info("用户 {} 对 blogId={} 的点赞记录数: {}", userId, blogId, count);

            if (count > 0) {
                int affected = noteLikeDOMapper.cancelLike(userId, blogId);
                if (affected <= 0) {
                    log.warn("用户 {} 取消点赞未命中有效记录 blogId={}", userId, blogId);
                    return Result.fail("取消点赞失败");
                }
                sendInteractionEvent(userId, blogId, NoteInteractionConstants.ACTION_UNLIKE, -1L);
                log.info("用户 {} 取消点赞 blogId={}", userId, blogId);
                return Result.ok("取消点赞成功");
            }

            NoteLikeDO noteLikeDO = new NoteLikeDO();
            noteLikeDO.setUserId(userId);
            noteLikeDO.setNoteId(blogId);
            noteLikeDOMapper.saveOrUpdateLike(noteLikeDO);
            sendInteractionEvent(userId, blogId, NoteInteractionConstants.ACTION_LIKE, 1L);
            log.info("用户 {} 点赞 blogId={}", userId, blogId);
            return Result.ok("点赞成功");
        } catch (Exception e) {
            log.error("点赞操作失败: ", e);
            return Result.fail("操作失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result collectNote(Long blogId) {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("用户 {} 尝试收藏/取消收藏 blogId={}", userId, blogId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.fail("用户未登录");
            }
            int count = noteCollectionDOMapper.countCollectionByUserAndNote(userId, blogId);
            log.info("用户 {} 对 blogId={} 的收藏记录数: {}", userId, blogId, count);

            if (count > 0) {
                int affected = noteCollectionDOMapper.cancelCollection(userId, blogId);
                if (affected <= 0) {
                    log.warn("用户 {} 取消收藏未命中有效记录 blogId={}", userId, blogId);
                    return Result.fail("取消收藏失败");
                }
                log.info("用户 {} 取消收藏 blogId={}", userId, blogId);
                sendInteractionEvent(userId, blogId, NoteInteractionConstants.ACTION_UNCOLLECT, -1L);
                return Result.ok("取消收藏成功");
            }

            NoteCollectionDO noteCollectionDO = new NoteCollectionDO();
            noteCollectionDO.setUserId(userId);
            noteCollectionDO.setNoteId(blogId);
            noteCollectionDOMapper.saveOrUpdateCollection(noteCollectionDO);
            sendInteractionEvent(userId, blogId, NoteInteractionConstants.ACTION_COLLECT, 1L);
            log.info("用户 {} 收藏 blogId={}", userId, blogId);
            return Result.ok("收藏成功");
        } catch (Exception e) {
            log.error("收藏操作失败: ", e);
            return Result.fail("操作失败：" + e.getMessage());
        }
    }

    @Override
    public Result isLikeNote(Long blogId) {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("检查用户 {} 是否点赞 blogId={}", userId, blogId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.ok(false);
            }
            int count = noteLikeDOMapper.countLikeByUserAndNote(userId, blogId);
            boolean liked = count > 0;
            log.info("用户 {} 对 blogId={} 的点赞状态: {}", userId, blogId, liked);
            return Result.ok(liked);
        } catch (Exception e) {
            log.error("查询点赞状态失败: ", e);
            return Result.fail("查询点赞状态失败：" + e.getMessage());
        }
    }

    @Override
    public Result isCollectNote(Long blogId) {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("检查用户 {} 是否收藏 blogId={}", userId, blogId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.ok(false);
            }
            int count = noteCollectionDOMapper.countCollectionByUserAndNote(userId, blogId);
            boolean collected = count > 0;
            log.info("用户 {} 对 blogId={} 的收藏状态: {}", userId, blogId, collected);
            return Result.ok(collected);
        } catch (Exception e) {
            log.error("查询收藏状态失败: ", e);
            return Result.fail("查询收藏状态失败：" + e.getMessage());
        }
    }

    @Override
    public Result likeList() {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("查询用户 {} 的点赞列表", userId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.fail("用户未登录");
            }
            List<Long> noteIdList = noteLikeDOMapper.listLikeByUserId(userId);
            log.info("用户 {} 点赞的笔记ID数量: {}", userId, noteIdList.size());

            List<BlogListDTO> list = noteIdsToBlogListDTO(noteIdList);
            log.info("转换完成，返回博客列表条数: {}", list.size());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("查询点赞列表失败: ", e);
            return Result.fail("查询点赞列表失败：" + e.getMessage());
        }
    }

    @Override
    public Result collectList() {
        try {
            Long userId = UserIdHolder.getUserId();
            log.info("查询用户 {} 的收藏列表", userId);
            if (userId == null) {
                log.warn("用户未登录");
                return Result.fail("用户未登录");
            }
            List<Long> noteIdList = noteCollectionDOMapper.listCollectionByUserId(userId);
            log.info("用户 {} 收藏的笔记ID数量: {}", userId, noteIdList.size());

            List<BlogListDTO> list = noteIdsToBlogListDTO(noteIdList);
            log.info("转换完成，返回博客列表条数: {}", list.size());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("查询收藏列表失败: ", e);
            return Result.fail("查询收藏列表失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeAdd(Long noteId, Long num) {
        try {
            if (noteId == null || num == null) {
                return Result.fail("参数错误：笔记ID不能为空，增加数量不能为负数");
            }

            int affected = noteCountDOMapper.incrementLikeTotal(noteId, num);
            if (affected > 0) {
                return Result.ok("点赞数增加成功");
            }
            return Result.fail("点赞数增加失败");
        } catch (Exception e) {
            log.error("增加点赞数失败: ", e);
            return Result.fail("系统异常：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result collectAdd(Long noteId, Long num) {
        try {
            if (noteId == null || num == null) {
                return Result.fail("参数错误：笔记ID不能为空，增加数量不能为负数");
            }

            int affected = noteCountDOMapper.incrementCollectTotal(noteId, num);
            if (affected > 0) {
                return Result.ok("收藏数增加成功");
            }
            return Result.fail("收藏数增加失败");
        } catch (Exception e) {
            log.error("增加收藏数失败: ", e);
            return Result.fail("系统异常：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result commentAdd(Long noteId, Long num) {
        try {
            if (noteId == null || num == null) {
                return Result.fail("参数错误：笔记ID不能为空，增加数量不能为负数");
            }

            int affected = noteCountDOMapper.incrementCommentTotal(noteId, num);
            if (affected > 0) {
                return Result.ok("评论数增加成功");
            }
            return Result.fail("评论数增加失败");
        } catch (Exception e) {
            log.error("增加评论数失败: ", e);
            return Result.fail("系统异常：" + e.getMessage());
        }
    }

    private void sendInteractionEvent(Long userId, Long blogId, String actionType, Long delta) {
        NoteInteractionEvent event = NoteInteractionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .noteId(blogId)
                .actionType(actionType)
                .delta(delta)
                .build();
        noteInteractionProducer.send(event);
    }

    private List<BlogListDTO> noteIdsToBlogListDTO(List<Long> noteIdList) {
        List<NoteBasicDO> noteBasicList = noteIdList.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(noteBasicMapper::selectByNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return noteBasicList.stream()
                .map(noteDO -> {
                    BlogListDTO dto = new BlogListDTO();
                    Long noteId = noteDO.getNoteId();
                    Long userId = noteDO.getUserId();
                    UserDTO byUserId = userAuthFeignApi.findByUserId(userId);
                    NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteId);
                    dto.setLikeTotal(noteCountDO != null ? noteCountDO.getLikeTotal() : 0L);
                    BeanUtils.copyProperties(noteDO, dto);
                    dto.setBlogId(noteDO.getNoteId());
                    dto.setUserDTO(byUserId);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
