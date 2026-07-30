package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.count.api.CountFeignApi;
import athena.count.api.constant.CountCounterConstants;
import athena.count.api.dto.CounterValueDTO;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.rpc.UserAuthFeignApi;
import athena.ground.biz.service.NoteSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NoteSearchServiceV1Impl implements NoteSearchService {

    private static final byte STATUS_APPROVED = 1;

    @Resource
    private NoteBasicDOMapper noteBasicDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserAuthFeignApi userAuthFeginApi;

    @Resource
    private CountFeignApi countFeignApi;

    @Override
    public Result searchPublicNotes(String keyword, Integer type, Integer channelId, Integer pageNum, Integer pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return Result.fail("搜索关键词不能为空");
        }
        pageNum = normalizePageNum(pageNum);
        pageSize = normalizePageSize(pageSize);
        int offset = (pageNum - 1) * pageSize;

        List<NoteBasicDO> noteBasicDOList = noteBasicDOMapper.searchApprovedByTitle(
                normalizedKeyword,
                STATUS_APPROVED,
                type,
                channelId,
                offset,
                pageSize
        );
        return Result.ok(toBlogList(noteBasicDOList));
    }

    @Override
    public Result searchMyNotes(String keyword, Byte status, Integer type, Integer pageNum, Integer pageSize) {
        Long userId = UserIdHolder.getUserId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return Result.fail("搜索关键词不能为空");
        }
        pageNum = normalizePageNum(pageNum);
        pageSize = normalizePageSize(pageSize);
        int offset = (pageNum - 1) * pageSize;

        List<NoteBasicDO> noteBasicDOList = noteBasicDOMapper.searchByUserIdAndTitle(
                userId,
                normalizedKeyword,
                status,
                type,
                offset,
                pageSize
        );
        return Result.ok(toBlogList(noteBasicDOList));
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalizedKeyword = keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return null;
        }
        return normalizedKeyword;
    }

    private Integer normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum <= 0) {
            return 1;
        }
        return pageNum;
    }

    private Integer normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 20);
    }

    private Long getCounter(Long noteId, String counterType) {
        try {
            Result<CounterValueDTO> result = countFeignApi.getOne(CountCounterConstants.SCOPE_NOTE, noteId);
            if (result == null || result.getData() == null || result.getData().getCounters() == null) {
                return fallbackNoteCounter(noteId, counterType);
            }
            return result.getData().getCounters().getOrDefault(counterType, fallbackNoteCounter(noteId, counterType));
        } catch (Exception e) {
            log.warn("[NoteSearchService] 读取计数中心失败, fallback DB, noteId={}, counterType={}", noteId, counterType, e);
            return fallbackNoteCounter(noteId, counterType);
        }
    }

    private Long fallbackNoteCounter(Long noteId, String counterType) {
        NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteId);
        if (noteCountDO == null) {
            return 0L;
        }
        if (CountCounterConstants.LIKE_TOTAL.equals(counterType)) {
            return noteCountDO.getLikeTotal() == null ? 0L : noteCountDO.getLikeTotal();
        }
        if (CountCounterConstants.COLLECT_TOTAL.equals(counterType)) {
            return noteCountDO.getCollectTotal() == null ? 0L : noteCountDO.getCollectTotal();
        }
        if (CountCounterConstants.COMMENT_TOTAL.equals(counterType)) {
            return noteCountDO.getCommentTotal() == null ? 0L : noteCountDO.getCommentTotal();
        }
        return 0L;
    }

    private List<BlogListDTO> toBlogList(List<NoteBasicDO> noteBasicDOList) {
        return noteBasicDOList.stream()
                .map(noteBasicDO -> {
                    BlogListDTO dto = new BlogListDTO();
                    BeanUtils.copyProperties(noteBasicDO, dto);
                    dto.setBlogId(noteBasicDO.getNoteId());
                    dto.setLikeTotal(getCounter(noteBasicDO.getNoteId(), CountCounterConstants.LIKE_TOTAL));
                    UserDTO byUserId = userAuthFeginApi.findByUserId(noteBasicDO.getUserId());
                    dto.setUserDTO(byUserId);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
