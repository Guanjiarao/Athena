package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.count.api.CountFeignApi;
import athena.count.api.constant.CountCounterConstants;
import athena.count.api.dto.CounterValueDTO;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteContentDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dataobject.NoteDO;
import athena.ground.biz.domain.dto.BlogAskDTO;
import athena.ground.biz.domain.dto.BlogDetailDTO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.dto.NoteSubmitDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteCollectionDOMapper;
import athena.ground.biz.domain.mapper.NoteContentDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.domain.mapper.NoteDOMapper;
import athena.ground.biz.domain.mapper.NoteLikeDOMapper;
import athena.ground.biz.domain.mapper.NoteTopicRelationMapper;
import athena.ground.biz.domain.mapper.UserViewRecordMapper;
import athena.ground.biz.mq.event.NoteTopicBuildEvent;
import athena.ground.biz.mq.producer.NoteTopicBuildProducer;
import athena.ground.biz.rpc.UserAuthFeignApi;
import athena.ground.biz.service.BlogAskService;
import athena.ground.biz.service.GroundService;
import athena.ground.biz.service.NoteInteractionService;
import athena.ground.biz.service.NotePublishService;
import athena.ground.biz.service.AthenaInsightNoteFeatureService;
import athena.ground.biz.service.AthenaNoteDocumentUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 广场模块服务实现
 */
@Slf4j
@Service
public class GroundServiceImpl implements GroundService {

    private static final byte STATUS_APPROVED = 1;

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private NoteDOMapper noteMapper;

    @Resource
    private NoteContentDOMapper noteContentDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;

    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;

    @Resource
    private NoteTopicRelationMapper noteTopicRelationMapper;

    @Resource
    private UserViewRecordMapper userViewRecordMapper;

    @Resource
    private UserAuthFeignApi userAuthFeginApi;

    @Resource
    private CountFeignApi countFeignApi;

    @Resource
    private NoteInteractionService noteInteractionService;

    @Resource
    private NotePublishService notePublishService;

    @Resource
    private NoteTopicBuildProducer noteTopicBuildProducer;

    @Resource
    private BlogAskService blogAskService;

    @Resource
    private AthenaInsightNoteFeatureService athenaInsightNoteFeatureService;

    @Resource
    private AthenaNoteDocumentUploadService athenaNoteDocumentUploadService;

    @Override
    public Result getBlogListPage(Integer pageNum, Integer pageSize) {
        log.info("开始查询广场博客列表，页码: {}，每页大小: {}", pageNum, pageSize);

        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;
        List<NoteBasicDO> noteBasicDOList = noteBasicMapper.selectApprovedByType(1, offset, pageSize);
        log.info("查询到当前页博客基础数据条数: {}", noteBasicDOList.size());

        try {
            List<BlogListDTO> list = basicToBlogDTO(noteBasicDOList);
            log.info("转换完成，返回博客列表条数: {}", list.size());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("查询博客前十条出错: ", e);
            return Result.fail("查询博客前十条出错了");
        }
    }

    @Override
    public Result getBlogListByChannelId(Integer channelId, Integer pageNum, Integer pageSize) {
        log.info("开始按频道ID查询文章列表，频道ID: {}，页码: {}，每页大小: {}", channelId, pageNum, pageSize);

        if (channelId == null || channelId <= 0) {
            log.warn("频道ID参数错误: {}", channelId);
            return Result.fail("频道ID不能为空且必须为正整数");
        }
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;

        List<NoteBasicDO> noteBasicDOList = noteBasicMapper.selectApprovedByChannelId(channelId, offset, pageSize);
        log.info("频道ID {} 查询到文章条数: {}", channelId, noteBasicDOList.size());

        try {
            List<BlogListDTO> list = basicToBlogDTO(noteBasicDOList);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("按频道ID查询文章列表出错: ", e);
            return Result.fail("按频道查询文章列表失败：" + e.getMessage());
        }
    }

    @Override
    public Result getBlogListByType(Integer type, Integer pageNum, Integer pageSize) {
        log.info("开始按笔记类型查询文章列表，类型: {}，页码: {}，每页大小: {}", type, pageNum, pageSize);

        if (type == null || type < 0) {
            log.warn("笔记类型参数错误: {}", type);
            return Result.fail("笔记类型不能为空且必须为正整数");
        }
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;

        List<NoteBasicDO> noteBasicDOList = noteBasicMapper.selectApprovedByType(type, offset, pageSize);
        log.info("笔记类型 {} 查询到文章条数: {}", type, noteBasicDOList.size());

        try {
            List<BlogListDTO> list = basicToBlogDTO(noteBasicDOList);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("按笔记类型查询文章列表出错: ", e);
            return Result.fail("按类型查询文章列表失败：" + e.getMessage());
        }
    }

    @Override
    public Result getBlogListByUserId(Long userId, Integer pageNum, Integer pageSize) {
        log.info("开始查询我的博客列表，页码: {}，每页大小: {}", pageNum, pageSize);

        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;
        if (userId == null || userId == 0) {
            userId = UserIdHolder.getUserId();
        }
        List<NoteBasicDO> noteBasicDOList = noteBasicMapper.selectByUserId(userId, offset, pageSize);
        log.info("查询到当前页博客基础数据条数: {}", noteBasicDOList.size());

        try {
            List<BlogListDTO> list = basicToBlogDTO(noteBasicDOList);
            log.info("转换完成，返回博客列表条数: {}", list.size());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("查询博客前十条出错: ", e);
            return Result.fail("查询博客前十条出错了");
        }
    }

    private List<BlogListDTO> basicToBlogDTO(List<NoteBasicDO> noteBasicDOList) {
        if (noteBasicDOList == null || noteBasicDOList.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询用户信息
        List<Long> userIds = noteBasicDOList.stream()
                .map(NoteBasicDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserDTO> userMap = buildUserMap(userIds);

        // 批量查询笔记统计数据：优先计数中心，失败时 buildNoteCountMap 内部兜底 note_count 表
        List<Long> noteIds = noteBasicDOList.stream()
                .map(NoteBasicDO::getNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, NoteCountDO> countMap = buildNoteCountMap(noteIds);

        return noteBasicDOList.stream()
                .map(noteBasicDO -> {
                    BlogListDTO dto = new BlogListDTO();
                    Long noteId = noteBasicDO.getNoteId();
                    Long userId = noteBasicDO.getUserId();

                    NoteCountDO noteCountDO = countMap.get(noteId);
                    dto.setLikeTotal(noteCountDO != null ? noteCountDO.getLikeTotal() : 0L);
                    BeanUtils.copyProperties(noteBasicDO, dto);
                    dto.setBlogId(noteBasicDO.getNoteId());
                    dto.setUserDTO(userMap.get(userId));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, UserDTO> buildUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserDTO> userDTOList = userAuthFeginApi.findByUserIds(userIds);
        if (userDTOList == null || userDTOList.isEmpty()) {
            return Collections.emptyMap();
        }
        return userDTOList.stream()
                .filter(Objects::nonNull)
                .filter(userDTO -> userDTO.getUserId() != null)
                .collect(Collectors.toMap(UserDTO::getUserId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, NoteCountDO> buildNoteCountMap(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, NoteCountDO> fallbackCountMap = buildFallbackNoteCountMap(noteIds);
        try {
            return noteIds.stream()
                    .distinct()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            noteId -> buildNoteCountDOFromCountCenter(noteId, fallbackCountMap.get(noteId)),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            log.warn("[GroundService] 批量读取计数中心失败, fallback DB, noteIds={}", noteIds, e);
            return fallbackCountMap;
        }
    }

    private Map<Long, NoteCountDO> buildFallbackNoteCountMap(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<NoteCountDO> countList = noteCountDOMapper.selectByNoteIds(noteIds);
        if (countList == null || countList.isEmpty()) {
            return Collections.emptyMap();
        }
        return countList.stream()
                .filter(Objects::nonNull)
                .filter(count -> count.getNoteId() != null)
                .collect(Collectors.toMap(NoteCountDO::getNoteId, Function.identity(), (left, right) -> left));
    }

    private NoteCountDO buildNoteCountDOFromCountCenter(Long noteId, NoteCountDO fallback) {
        Result<CounterValueDTO> result = countFeignApi.getOne(CountCounterConstants.SCOPE_NOTE, noteId);
        if (result == null || result.getData() == null || result.getData().getCounters() == null) {
            return fallback == null ? emptyNoteCount(noteId) : fallback;
        }
        Map<String, Long> counters = result.getData().getCounters();
        NoteCountDO noteCountDO = new NoteCountDO();
        noteCountDO.setNoteId(noteId);
        noteCountDO.setLikeTotal(counters.getOrDefault(CountCounterConstants.LIKE_TOTAL, fallbackValue(fallback, CountCounterConstants.LIKE_TOTAL)));
        noteCountDO.setCollectTotal(counters.getOrDefault(CountCounterConstants.COLLECT_TOTAL, fallbackValue(fallback, CountCounterConstants.COLLECT_TOTAL)));
        noteCountDO.setCommentTotal(counters.getOrDefault(CountCounterConstants.COMMENT_TOTAL, fallbackValue(fallback, CountCounterConstants.COMMENT_TOTAL)));
        return noteCountDO;
    }

    private NoteCountDO emptyNoteCount(Long noteId) {
        NoteCountDO noteCountDO = new NoteCountDO();
        noteCountDO.setNoteId(noteId);
        noteCountDO.setLikeTotal(0L);
        noteCountDO.setCollectTotal(0L);
        noteCountDO.setCommentTotal(0L);
        return noteCountDO;
    }

    private Long fallbackValue(NoteCountDO fallback, String counterType) {
        if (fallback == null) {
            return 0L;
        }
        if (CountCounterConstants.LIKE_TOTAL.equals(counterType)) {
            return fallback.getLikeTotal() == null ? 0L : fallback.getLikeTotal();
        }
        if (CountCounterConstants.COLLECT_TOTAL.equals(counterType)) {
            return fallback.getCollectTotal() == null ? 0L : fallback.getCollectTotal();
        }
        if (CountCounterConstants.COMMENT_TOTAL.equals(counterType)) {
            return fallback.getCommentTotal() == null ? 0L : fallback.getCommentTotal();
        }
        return 0L;
    }

    @Override
    public Result getBlogDetail(Long noteId) {
        log.info("开始查询博客详情, noteId={}", noteId);
        if (noteId == null) {
            return Result.fail("noteId不能为空");
        }

        NoteBasicDO noteBasicDO = noteBasicMapper.selectApprovedByNoteId(noteId);
        if (noteBasicDO == null) {
            log.warn("博客不存在或未通过审核, noteId={}", noteId);
            return Result.fail("博客不存在或未通过审核");
        }

        NoteDO noteDO = noteMapper.selectByPrimaryKey(noteId);
        NoteContentDO noteContentDO = noteContentDOMapper.selectByNoteId(noteId);
        NoteCountDO fallbackNoteCountDO = noteCountDOMapper.selectByNoteId(noteId);
        NoteCountDO noteCountDO = buildNoteCountDOFromCountCenter(noteId, fallbackNoteCountDO);
        if (noteDO == null) {
            log.warn("博客详情不存在, noteId={}", noteId);
            return Result.fail("博客不存在");
        }

        log.info("查询到博客详情: {}", noteDO);
        UserDTO byUserId = userAuthFeginApi.findByUserId(noteDO.getUserId());
        BlogDetailDTO dto = new BlogDetailDTO();
        BeanUtils.copyProperties(noteDO, dto);
        BeanUtils.copyProperties(noteBasicDO, dto);
        if (noteContentDO != null) {
            BeanUtils.copyProperties(noteContentDO, dto);
        }
        if (noteCountDO != null) {
            BeanUtils.copyProperties(noteCountDO, dto);
        }
        dto.setStatus(noteBasicDO.getStatus() == null ? STATUS_APPROVED : noteBasicDO.getStatus());
        dto.setId(noteId);
        dto.setUserDTO(byUserId);
        return Result.ok(dto);
    }

    @Override
    public Result getNoteBasicListByNoteIdList(List<Long> noteIdList) {
        if (CollectionUtils.isEmpty(noteIdList)) {
            return Result.fail("noteIdList不能为空");
        }

        List<Long> distinctNoteIdList = noteIdList.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(distinctNoteIdList)) {
            return Result.fail("noteIdList不能为空");
        }

        List<NoteBasicDO> noteBasicDOList = noteBasicMapper.selectByNoteIdList(distinctNoteIdList);
        if (CollectionUtils.isEmpty(noteBasicDOList)) {
            return Result.ok(new ArrayList<>());
        }

        Map<Long, NoteBasicDO> noteBasicMap = noteBasicDOList.stream()
                .collect(Collectors.toMap(NoteBasicDO::getNoteId, item -> item, (left, right) -> left, LinkedHashMap::new));

        // 批量查询用户信息和统计数据
        List<Long> userIds = noteBasicDOList.stream()
                .map(NoteBasicDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserDTO> userMap = buildUserMap(userIds);
        Map<Long, NoteCountDO> countMap = buildNoteCountMap(distinctNoteIdList);

        List<BlogListDTO> resultList = distinctNoteIdList.stream()
                .map(noteBasicMap::get)
                .filter(java.util.Objects::nonNull)
                .map(noteBasicDO -> {
                    BlogListDTO dto = new BlogListDTO();
                    BeanUtils.copyProperties(noteBasicDO, dto);
                    dto.setBlogId(noteBasicDO.getNoteId());
                    NoteCountDO noteCountDO = countMap.get(noteBasicDO.getNoteId());
                    dto.setLikeTotal(noteCountDO == null ? 0L : noteCountDO.getLikeTotal());
                    dto.setUserDTO(userMap.get(noteBasicDO.getUserId()));
                    return dto;
                })
                .collect(Collectors.toList());
        return Result.ok(resultList);
    }

    @Override
    public Result submitNote(NoteSubmitDTO noteSubmitDTO) {
        log.info("开始提交笔记: {}", noteSubmitDTO);
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            log.warn("用户ID为空");
            return Result.fail("用户ID不能为空");
        }
        noteSubmitDTO.setUserId(userId);
        if (!StringUtils.hasText(noteSubmitDTO.getTitle())) {
            log.warn("笔记标题为空");
            return Result.fail("笔记标题不能为空");
        }
        if (noteSubmitDTO.getType() == null) {
            log.warn("笔记类型为空");
            return Result.fail("笔记类型不能为空");
        }

        try {
            Long noteId = notePublishService.publish(noteSubmitDTO);
            noteTopicBuildProducer.send(NoteTopicBuildEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .noteId(noteId)
                    .authorId(noteSubmitDTO.getUserId())
                    .title(noteSubmitDTO.getTitle())
                    .content(noteSubmitDTO.getContent())
                    .type(noteSubmitDTO.getType() == null ? null : Integer.valueOf(noteSubmitDTO.getType()))
                    .channelId(noteSubmitDTO.getChannelId())
                    .build());
            Result<Long> result = Result.ok(noteId);
            result.setMessage("提交成功，等待审核");
            return result;
        } catch (Exception e) {
            log.error("笔记提交失败", e);
            return Result.fail(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteNote(Long noteId) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        if (noteId == null) {
            return Result.fail("noteId不能为空");
        }

        NoteBasicDO noteBasicDO = noteBasicMapper.selectByNoteId(noteId);
        if (noteBasicDO == null) {
            return Result.fail("笔记不存在");
        }


        try {
            athenaInsightNoteFeatureService.deleteByNoteId(noteId);
            if (shouldSyncRag(noteBasicDO.getType())) {
                athenaNoteDocumentUploadService.deleteByNoteId(noteId, noteBasicDO.getType(), userId);
            }
            noteTopicRelationMapper.deleteByNoteId(noteId);
            noteLikeDOMapper.deleteByNoteId(noteId);
            noteCollectionDOMapper.deleteByNoteId(noteId);
            userViewRecordMapper.deleteByNoteId(noteId);
            noteContentDOMapper.deleteByNoteId(noteId);
            noteCountDOMapper.deleteByNoteId(noteId);
            noteMapper.deleteByPrimaryKey(noteId);
            noteBasicMapper.deleteByPrimaryKey(noteId);
            return Result.ok();
        } catch (Exception e) {
            log.error("删除笔记失败, noteId={}, userId={}", noteId, userId, e);
            return Result.fail("删除笔记失败：" + e.getMessage());
        }
    }

    private boolean shouldSyncRag(Byte type) {
        return type != null && type != 0 && type != 1 && type != 2;
    }

    @Override
    public Result askBlog(BlogAskDTO request) {
        try {
            return Result.ok(blogAskService.ask(request));
        } catch (Exception e) {
            log.error("博客问答失败", e);
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result likeNote(Long blogId) {
        return noteInteractionService.likeNote(blogId);
    }

    @Override
    public Result collectNote(Long blogId) {
        return noteInteractionService.collectNote(blogId);
    }

    @Override
    public Result isLikeNote(Long blogId) {
        return noteInteractionService.isLikeNote(blogId);
    }

    @Override
    public Result isCollectNote(Long blogId) {
        return noteInteractionService.isCollectNote(blogId);
    }

    @Override
    public Result likeList() {
        return noteInteractionService.likeList();
    }

    @Override
    public Result collectList() {
        return noteInteractionService.collectList();
    }

    @Override
    public Result collectAdd(Long noteId, Long num) {
        return noteInteractionService.collectAdd(noteId, num);
    }

    @Override
    public Result likeAdd(Long noteId, Long num) {
        return noteInteractionService.likeAdd(noteId, num);
    }

    @Override
    public Result commentAdd(Long noteId, Long num) {
        return noteInteractionService.commentAdd(noteId, num);
    }
}
