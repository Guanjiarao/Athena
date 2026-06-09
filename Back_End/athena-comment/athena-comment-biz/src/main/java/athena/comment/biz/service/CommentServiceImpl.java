package athena.comment.biz.service;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.comment.biz.domain.dataobject.CommentDO;
import athena.comment.biz.domain.dataobject.CommentLikeDO;
import athena.comment.biz.domain.dto.CommentBasicDTO;
import athena.comment.biz.domain.dto.ChildCommentDTO;
import athena.comment.biz.domain.mapper.CommentContentDOMapper;
import athena.comment.biz.domain.mapper.CommentDOMapper;
import athena.comment.biz.domain.mapper.CommentLikeDOMapper;
import athena.comment.biz.domain.vo.PublishCommentVO;
import athena.comment.biz.rpc.UserAuthFeignApi;
import athena.count.api.CountFeignApi;
import athena.count.api.constant.CountCounterConstants;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterValueDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 评论业务层实现类
 * 处理评论的查询、发布、二级评论加载等核心业务逻辑
 *
 * @author 开发者名称
 * @date 2026-03-07
 */
@Slf4j
@Service
public class CommentServiceImpl implements CommentService {

    /**
     * 评论主表Mapper
     */
    @Resource
    private CommentDOMapper commentDOMapper;

    /**
     * 评论内容表Mapper
     */
    @Resource
    private CommentContentDOMapper commentContentDOMapper;

    /**
     * 评论点赞表Mapper（当前代码暂未使用，预留扩展）
     */
    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;

    /**
     * 用户认证服务RPC调用接口
     * 用于通过用户ID查询用户基础信息
     */
    @Resource
    private UserAuthFeignApi userAuthFeignApi;

    @Resource
    private CountFeignApi countFeignApi;

    @Resource
    private MessageQueueProducer messageQueueProducer;



    /**
     * 分页查询笔记的一级评论列表
     *
     * @param blogId   笔记ID（对应数据库note_id字段）
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @return Result<List<CommentBasicDTO>> 一级评论列表DTO集合
     */
    @Override
    public Result commentListPage(Long blogId, Long pageNum, Long pageSize) {
        log.info("【评论查询接口】开始执行：笔记ID={}, 页码={}, 每页条数={}", blogId, pageNum, pageSize);

        try {
            // 1. 计算分页偏移量（MyBatis分页LIMIT offset, pageSize，offset从0开始）
            Long offset = (pageNum - 1) * pageSize;
            log.debug("【分页计算】笔记ID={}，计算偏移量offset={}", blogId, offset);

            // 2. 查询一级评论列表（level=1）
            List<CommentDO> commentDOList = commentDOMapper.selectFirstLevelCommentsByNoteId(blogId, offset, pageSize);
            log.info("【数据库查询】笔记ID={}，查询到一级评论总数={}", blogId, commentDOList.size());

            // 3. DO转DTO：将数据库实体转换为前端展示的DTO
            List<CommentBasicDTO> dtoList = new ArrayList<>();
            for (CommentDO commentDO : commentDOList) {
                // 调用转换方法，将CommentDO转换为CommentBasicDTO
                CommentBasicDTO dto = commentDoToBasicDTO(commentDO);
                dtoList.add(dto);
            }

            log.info("【评论查询接口】执行完成：笔记ID={}，最终返回评论数={}", blogId, dtoList.size());
            return Result.ok(dtoList);
        } catch (Exception e) {
            log.error("【评论查询接口】执行异常：笔记ID={}", blogId, e);
            return Result.fail("查询评论失败");
        }
    }

    /**
     * 发布评论（支持一级/二级评论）
     * 事务注解：发生任何异常时回滚，保证评论主表和内容表数据一致性
     *
     * @param publishCommentVO 发布评论的入参VO
     * @return Result 发布结果（成功/失败）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result publishComment(PublishCommentVO publishCommentVO) {
        log.info("【发布评论接口】开始执行：入参={}", publishCommentVO);

        try {
            // 1. 获取当前登录用户ID（从ThreadLocal中获取，需提前通过拦截器设置）
            Long userId = UserIdHolder.getUserId();
            if (userId == null) {
                log.warn("【发布评论失败】用户未登录");
                return Result.fail("用户未登录");
            }
            log.info("【用户身份确认】登录用户ID={}", userId);

            // 2. 构建评论DO实体，封装数据库存储字段
            CommentDO commentDO = new CommentDO();
            // 关联笔记ID
            commentDO.setNoteId(publishCommentVO.getBlogId());
            // 评论发布者ID
            commentDO.setUserId(userId);
            // 评论图片URL
            commentDO.setImageUrl(publishCommentVO.getImageUrl());
            // 回复的评论ID（0表示对笔记直接评论）
            commentDO.setReplyCommentId(publishCommentVO.getReplyCommentId());

            // 3. 区分一级/二级评论，设置不同字段
            if (publishCommentVO.getReplyCommentId() == 0) {
                // 3.1 一级评论：直接评论笔记
                commentDO.setLevel( 1);          // 级别1：一级评论
                commentDO.setParentId(publishCommentVO.getBlogId()); // 父ID=笔记ID
                commentDO.setReplyUserId(0L);          // 回复用户ID=0（无回复对象）
                log.info("【评论类型判定】检测到一级评论操作：笔记ID={}", publishCommentVO.getBlogId());
            } else {
                // 3.2 二级评论：回复已有评论
                commentDO.setLevel( 2);          // 级别2：二级评论
                commentDO.setParentId(publishCommentVO.getReplyCommentId()); // 父ID=被回复的一级评论ID
                // 查询被回复评论的发布者ID，作为回复用户ID
                Long replyUserId = commentDOMapper.selectUserIdByCommentId(publishCommentVO.getReplyCommentId());
                commentDO.setReplyUserId(replyUserId == null ? -1 : replyUserId); // 无数据时设为-1
                log.info("【评论类型判定】检测到二级评论操作：回复评论ID={}，目标用户ID={}",
                        publishCommentVO.getReplyCommentId(), replyUserId);
            }

            // 4. 判断评论内容是否为空（空则不插入内容表）
            boolean isContentEmpty = publishCommentVO.getContent() == null || publishCommentVO.getContent().trim().isEmpty();
            commentDO.setIsContentEmpty(isContentEmpty);
            log.info("【内容校验】评论内容是否为空：{}", isContentEmpty);

            // 5. 插入评论主表（自增ID会自动回填到commentDO.getId()）
            commentDOMapper.insertComment(commentDO);
            log.info("【主表插入】评论主表插入成功：新评论ID={}", commentDO.getId());

            // 6. 若内容非空，插入评论内容表（拆分表设计，避免大字段影响查询性能）
            if (!isContentEmpty) {
                commentContentDOMapper.insertCommentContent(commentDO.getId(), publishCommentVO.getContent());
                log.info("【内容表插入】评论内容表插入成功：评论ID={}", commentDO.getId());
            }

            // 7. 二级评论：更新一级评论的回复数和首个回复评论ID
            if (2 == commentDO.getLevel()) {
                commentDOMapper.incrementReplyTotalAndFirstComment(publishCommentVO.getReplyCommentId(), commentDO.getId());
                log.info("【关联更新】更新一级评论回复数：一级评论ID={}，新增二级评论ID={}",
                        publishCommentVO.getReplyCommentId(), commentDO.getId());
            }
            // 评论发布成功后交给计数中心异步聚合，评论服务不再同步改笔记计数字段
            sendCounterDelta(CountCounterConstants.SCOPE_NOTE, publishCommentVO.getBlogId(), CountCounterConstants.COMMENT_TOTAL, 1L);

            log.info("【发布评论接口】执行成功：最终生成评论ID={}", commentDO.getId());
            return Result.ok("发布成功");
        } catch (Exception e) {
            log.error("【发布评论接口】执行异常：入参={}", publishCommentVO, e);
            // 异常由Transactional注解自动回滚
            return Result.fail("发布评论失败");
        }
    }

    /**
     * 分页查询一级评论下的二级评论列表
     * 注：当前代码为占位逻辑，需补充完整实现
     *
     * @param commentId 一级评论ID（父评论ID）
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @return Result 二级评论分页结果
     */
    @Override
    public Result extendComment(Long commentId, Long pageNum, Long pageSize) {
        log.info("【加载二级评论接口】开始执行：一级评论ID={}, 页码={}, 每页条数={}", commentId, pageNum, pageSize);

        try {
            // TODO 待实现：补充二级评论查询逻辑
            // 1. 计算分页偏移量：
            long offset = (pageNum - 1) * pageSize;
            // 2. 查询二级评论列表：
            List<Long> commentIds = commentDOMapper.selectSecondLevelCommentsByParentId(commentId, offset, pageSize);
            // 3. DO转DTO：调用commentIdtoChildDTO方法转换为ChildCommentDTO
            List<ChildCommentDTO> dtoList = commentIds.stream()
                    // 过滤掉null的DO（防止空指针）
                    .filter(commentDO -> commentDO != null)
                    // 转换为ChildCommentDTO
                    .map(this::commentIdtoChildDTO)
                    // 过滤掉转换失败的DTO（null）
                    .filter(Objects::nonNull)
                    // 收集为List
                    .toList();

            log.info("加载二级接口完毕");
            return Result.ok(dtoList);
        } catch (Exception e) {
            log.error("【加载二级评论接口】执行异常：一级评论ID={}", commentId, e);
            return Result.fail("加载二级评论失败");
        }
    }

    // 核心：点赞/取消点赞实现
    @Override
    public Result commentLike(Long commentId) {
        // 1. 获取当前登录用户ID
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录，无法点赞");
        }

        // 2. 查询当前用户对该评论的点赞记录
        CommentLikeDO commentLikeDO = commentLikeDOMapper.selectByUserIdAndCommentId(userId, commentId);

        try {
            if (commentLikeDO == null) {
                // 3. 无记录：新增点赞（status=1 表示点赞）
                CommentLikeDO newLike = new CommentLikeDO();
                newLike.setUserId(userId);
                newLike.setCommentId(commentId);
                newLike.setCreateTime(LocalDateTime.now());
                newLike.setStatus(1); // 1=点赞，0=取消点赞
                commentLikeDOMapper.insert(newLike);
                sendCounterDelta(CountCounterConstants.SCOPE_COMMENT, commentId, CountCounterConstants.LIKE_TOTAL, 1L);

                return Result.ok("点赞成功");
            } else {
                // 4. 有记录：翻转状态
                Integer currentStatus = commentLikeDO.getStatus();
                Integer newStatus = currentStatus == 1 ? 0 : 1;
                commentLikeDOMapper.updateStatusById(commentLikeDO.getId(), newStatus);
                sendCounterDelta(CountCounterConstants.SCOPE_COMMENT, commentId, CountCounterConstants.LIKE_TOTAL, newStatus == 1 ? 1L : -1L);

                String msg = newStatus == 1 ? "点赞成功" : "取消点赞成功";
                return Result.ok(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("操作失败：" + e.getMessage());
        }
    }

    private void sendCounterDelta(String scope, Long targetId, String counterType, Long delta) {
        CounterDeltaDTO deltaDTO = new CounterDeltaDTO();
        deltaDTO.setScope(scope);
        deltaDTO.setTargetId(targetId);
        deltaDTO.setCounterType(counterType);
        deltaDTO.setDelta(delta);
        deltaDTO.setEventId(UUID.randomUUID().toString());
        messageQueueProducer.send(CountCounterConstants.EVENT_TOPIC, deltaDTO.getEventId(), CountCounterConstants.EVENT_BIZ_DESC, deltaDTO);
    }

    private Long getCounter(String scope, Long targetId, String counterType, Long fallbackValue) {
        try {
            Result<CounterValueDTO> result = countFeignApi.getOne(scope, targetId);
            if (result == null || result.getData() == null || result.getData().getCounters() == null) {
                return fallbackValue == null ? 0L : fallbackValue;
            }
            return result.getData().getCounters().getOrDefault(counterType, fallbackValue == null ? 0L : fallbackValue);
        } catch (Exception e) {
            log.warn("[CommentService] 读取计数中心失败, fallback DB, scope={}, targetId={}, counterType={}",
                    scope, targetId, counterType, e);
            return fallbackValue == null ? 0L : fallbackValue;
        }
    }

    @Override
    public Result isCommentLike(Long commentId) {
        // 1. 获取当前用户ID
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录，无法查询点赞状态");
        }

        try {
            // 2. 查询点赞记录
            CommentLikeDO commentLikeDO = commentLikeDOMapper.selectByUserIdAndCommentId(userId, commentId);
            // 3. 判断状态：有记录且status=1 → 已点赞（true），否则未点赞（false）
            boolean isLiked = commentLikeDO != null && commentLikeDO.getStatus() == 1;
            return Result.ok(isLiked); // 将布尔值放入返回结果的data中
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("查询点赞状态失败：" + e.getMessage());
        }
    }

    /**
     * 根据评论ID查询发布者的用户信息
     * 内部工具方法：通过RPC调用用户服务获取用户基础信息
     *
     * @param commentId 评论ID
     * @return UserDTO 用户基础信息DTO（包含昵称、头像等）
     */
    private UserDTO commentIdToUserBasic(Long commentId) {
        log.debug("【内部调用】commentIdToUserBasic：根据评论ID={}查询用户", commentId);

        try {
            // 1. 根据评论ID查询发布者用户ID
            Long userId = commentDOMapper.selectUserIdByCommentId(commentId);
            if (userId == null) {
                log.warn("【内部调用失败】评论ID={} 无对应用户ID，无法查询用户信息", commentId);
                return null;
            }

            // 2. RPC调用用户认证服务，查询用户基础信息
            UserDTO userDTO = userAuthFeignApi.findByUserId(userId);
            log.debug("【RPC调用】调用用户服务查询用户ID={}，结果={}", userId, userDTO);

            return userDTO;
        } catch (Exception e) {
            log.error("【内部调用异常】commentIdToUserBasic：评论ID={}", commentId, e);
            return null;
        }
    }

    /**
     * 根据评论ID查询评论内容
     * 内部工具方法：从评论内容表查询文本内容
     *
     * @param commentId 评论ID
     * @return String 评论内容（空则返回null）
     */
    private String commentIdTotext(Long commentId) {
        log.debug("【内部调用】commentIdTotext：根据评论ID={}查询内容", commentId);

        if (commentId == null || commentId <= 0) {
            log.warn("【内部调用失败】评论ID={} 不合法", commentId);
            return null;
        }

        try {
            String content = commentContentDOMapper.selectCommentContentByCommentId(commentId);
            log.debug("【数据库查询】评论ID={}，查询到内容={}", commentId, content);
            return content;
        } catch (Exception e) {
            log.error("【内部调用异常】commentIdTotext：评论ID={}", commentId, e);
            return null;
        }
    }

    /**
     * 将CommentDO转换为CommentBasicDTO（一级评论展示DTO）
     * 封装一级评论的核心展示字段，包含用户信息、评论内容、首个回复等
     *
     * @param c 评论数据库实体
     * @return CommentBasicDTO 一级评论DTO
     */
    private CommentBasicDTO commentDoToBasicDTO(CommentDO c) {
        log.debug("【内部调用】commentDoToBasic：开始转换评论DO={}", c);

        try {
            // 1. 基础字段赋值
            Long commentId = c.getId();                     // 评论ID
            UserDTO userDTO = commentIdToUserBasic(commentId); // 发布者用户信息
            String content = commentIdTotext(commentId);    // 评论内容
            Long firstReplyCommentId = c.getFirstReplyCommentId(); // 首个回复评论ID

            // 2. 构建首个回复的二级评论DTO
            ChildCommentDTO childCommentDTO = commentIdtoChildDTO(firstReplyCommentId);

            // 3. 构建并返回一级评论DTO
            CommentBasicDTO dto = new CommentBasicDTO(
                    commentId,
                    c.getNoteId(),
                    userDTO,
                    content,
                    c.getImageUrl(),
                    c.getCreateTime(),
                    getCounter(CountCounterConstants.SCOPE_COMMENT, commentId, CountCounterConstants.LIKE_TOTAL, c.getLikeTotal()),
                    c.getReplyTotal(),
                    c.getIsTop(),
                    c.getHeat(),
                    childCommentDTO
            );

            log.debug("【内部调用完成】commentDoToBasic：转换成功，生成DTO={}", dto);
            return dto;
        } catch (Exception e) {
            log.error("【内部调用异常】commentDoToBasic：评论DO={}", c, e);
            return null;
        }
    }

    /**
     * 根据评论ID转换为ChildCommentDTO（二级评论展示DTO）
     * 封装二级评论的核心展示字段，包含回复用户昵称等
     *
     * @param commentId 评论ID（二级评论ID）
     * @return ChildCommentDTO 二级评论DTO
     */
    private ChildCommentDTO commentIdtoChildDTO(Long commentId) {
        log.debug("【内部调用】commentIdtoChildDTO：根据评论ID={}构建二级评论DTO", commentId);

        // 1. 空值校验：评论ID无效时返回null
        if (commentId == null || commentId <= 0) {
            log.warn("【内部调用返回】评论ID={} 不合法，直接返回null", commentId);
            return null;
        }

        try {
            // 2. 查询二级评论完整信息
            CommentDO commentDO = commentDOMapper.selectAllById(commentId);
            if (commentDO == null) {
                log.warn("【内部调用返回】评论ID={} 不存在，返回null", commentId);
                return null;
            }

            // 3. 查询发布者用户信息
            UserDTO userDTO = commentIdToUserBasic(commentId);
            // 4. 查询被回复用户信息（用于展示“回复XXX”）
            UserDTO replyUser = userAuthFeignApi.findByUserId(commentDO.getReplyUserId());
            // 5. 查询评论内容
            String content = commentIdTotext(commentId);

            // 6. 构建二级评论DTO（使用Builder模式）
            ChildCommentDTO dto = ChildCommentDTO.builder()
                    .heat(commentDO.getHeat())                // 评论热度
                    .replyCommentId(commentDO.getReplyCommentId()) // 回复的评论ID
                    .noteId(commentDO.getNoteId())            // 关联笔记ID
                    .likeTotal(commentDO.getLikeTotal())      // 点赞数
                    .parentId(commentDO.getParentId())        // 父评论ID
                    .replyUserId(commentDO.getReplyUserId())  // 回复的用户ID
                    .createTime(commentDO.getCreateTime())
                    .likeTotal(getCounter(CountCounterConstants.SCOPE_COMMENT, commentId, CountCounterConstants.LIKE_TOTAL, commentDO.getLikeTotal()))
                    .userDTO(userDTO)                         // 发布者用户信息
                    .replyUserName(replyUser == null ? null : replyUser.getNickName()) // 被回复者昵称
                    .imageUrl(commentDO.getImageUrl())        // 图片URL
                    .content(content)                         // 评论内容
                    .commentId(commentId)                     // 评论ID
                    .build();

            log.debug("【内部调用完成】commentIdtoChildDTO：构建成功，二级评论DTO={}", dto);
            return dto;
        } catch (Exception e) {
            log.error("【内部调用异常】commentIdtoChildDTO：评论ID={}", commentId, e);
            return null;
        }


    }
}