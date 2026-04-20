package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteContentDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dataobject.NoteDO;
import athena.ground.biz.domain.dto.BlogDetailDTO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.dto.NoteApproveDTO;
import athena.ground.biz.domain.dto.NoteRejectDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteContentDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.domain.mapper.NoteDOMapper;
import athena.ground.biz.rpc.InsightRpc;
import athena.ground.biz.rpc.UserAuthFeginApi;
import athena.ground.biz.service.AthenaNoteDocumentUploadService;
import athena.ground.biz.service.NoteReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NoteReviewServiceImpl implements NoteReviewService {

    private static final byte STATUS_PENDING = 0;
    private static final byte STATUS_APPROVED = 1;
    private static final byte STATUS_REJECTED = 2;

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private NoteDOMapper noteDOMapper;

    @Resource
    private NoteContentDOMapper noteContentDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserAuthFeginApi userAuthFeginApi;

    @Resource
    private InsightRpc insightRpc;

    @Resource
    private AthenaNoteDocumentUploadService athenaNoteDocumentUploadService;

    @Override
    public Result getPendingList(Integer pageNum, Integer pageSize, Byte type, Integer channelId) {
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNum - 1) * pageSize;
        List<NoteBasicDO> pendingList = noteBasicMapper.selectPendingPage(type, channelId, offset, pageSize);
        Map<Long, UserDTO> userMap = buildUserMap(pendingList.stream()
                .map(NoteBasicDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));

        List<BlogListDTO> result = pendingList.stream().map(noteBasicDO -> {
            BlogListDTO dto = new BlogListDTO();
            BeanUtils.copyProperties(noteBasicDO, dto);
            dto.setBlogId(noteBasicDO.getNoteId());
            NoteCountDO countDO = noteCountDOMapper.selectByNoteId(noteBasicDO.getNoteId());
            dto.setLikeTotal(countDO == null ? 0L : countDO.getLikeTotal());
            dto.setUserDTO(userMap.get(noteBasicDO.getUserId()));
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Override
    public Result getReviewDetail(Long noteId) {
        if (noteId == null) {
            return Result.fail("noteId不能为空");
        }
        NoteBasicDO noteBasicDO = noteBasicMapper.selectByNoteId(noteId);
        if (noteBasicDO == null) {
            return Result.fail("文章不存在");
        }
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        NoteContentDO noteContentDO = noteContentDOMapper.selectByNoteId(noteId);
        NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteId);

        BlogDetailDTO dto = new BlogDetailDTO();
        BeanUtils.copyProperties(noteBasicDO, dto);
        if (noteDO != null) {
            BeanUtils.copyProperties(noteDO, dto);
        }
        if (noteContentDO != null) {
            BeanUtils.copyProperties(noteContentDO, dto);
        }
        if (noteCountDO != null) {
            BeanUtils.copyProperties(noteCountDO, dto);
        }
        dto.setId(noteId);
        dto.setStatus(noteBasicDO.getStatus());
        dto.setUserDTO(userAuthFeginApi.findByUserId(noteBasicDO.getUserId()));
        return Result.ok(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result approve(NoteApproveDTO request) {
        if (request == null || request.getNoteId() == null) {
            return Result.fail("noteId不能为空");
        }

        ReviewUploadPayload payload;
        try {
            payload = buildApprovePayloadAndUpdateStatus(request.getNoteId(), UserIdHolder.getUserId());
        } catch (Exception e) {
            log.error("审核通过失败, noteId={}", request.getNoteId(), e);
            return Result.fail(e.getMessage());
        }

        if (shouldUploadAsRagentDocument(payload.type())) {
            try {
                athenaNoteDocumentUploadService.upload(
                        payload.noteId(),
                        payload.title(),
                        payload.content(),
                        payload.type(),
                        payload.authorId()
                );
            } catch (Exception e) {
                log.error("审核通过后上传知识库失败, noteId={}", payload.noteId(), e);
            }
        }
        insightRpc.refreshNoteFeature(payload.noteId());
        Result<Void> result = Result.ok();
        result.setMessage("审核通过");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result reject(NoteRejectDTO request) {
        if (request == null || request.getNoteId() == null) {
            return Result.fail("noteId不能为空");
        }
        if (!StringUtils.hasText(request.getReviewRemark())) {
            return Result.fail("驳回原因不能为空");
        }
        try {
            updateRejectStatus(request.getNoteId(), UserIdHolder.getUserId(), request.getReviewRemark());
            insightRpc.refreshNoteFeature(request.getNoteId());
            Result<Void> result = Result.ok();
            result.setMessage("审核拒绝成功");
            return result;
        } catch (Exception e) {
            log.error("审核拒绝失败, noteId={}", request.getNoteId(), e);
            return Result.fail(e.getMessage());
        }
    }

    private ReviewUploadPayload buildApprovePayloadAndUpdateStatus(Long noteId, Long reviewerId) {
        NoteBasicDO noteBasicDO = noteBasicMapper.selectByNoteId(noteId);
        if (noteBasicDO == null) {
            throw new IllegalStateException("文章不存在");
        }
        if (noteBasicDO.getStatus() == null || noteBasicDO.getStatus() != STATUS_PENDING) {
            throw new IllegalStateException("当前文章不处于待审核状态");
        }

        NoteBasicDO update = new NoteBasicDO();
        update.setNoteId(noteId);
        update.setStatus(STATUS_APPROVED);
        update.setReviewerId(reviewerId);
        update.setReviewTime(LocalDateTime.now());
        noteBasicMapper.updateByPrimaryKeySelective(update);

        NoteContentDO noteContentDO = noteContentDOMapper.selectByNoteId(noteId);
        return new ReviewUploadPayload(
                noteId,
                noteBasicDO.getTitle(),
                noteContentDO == null ? null : noteContentDO.getContent(),
                noteBasicDO.getType(),
                noteBasicDO.getUserId()
        );
    }

    private void updateRejectStatus(Long noteId, Long reviewerId, String reviewRemark) {
        NoteBasicDO noteBasicDO = noteBasicMapper.selectByNoteId(noteId);
        if (noteBasicDO == null) {
            throw new IllegalStateException("文章不存在");
        }
        if (noteBasicDO.getStatus() == null || noteBasicDO.getStatus() != STATUS_PENDING) {
            throw new IllegalStateException("当前文章不处于待审核状态");
        }

        NoteBasicDO update = new NoteBasicDO();
        update.setNoteId(noteId);
        update.setStatus(STATUS_REJECTED);
        update.setReviewerId(reviewerId);
        update.setReviewTime(LocalDateTime.now());
        update.setReviewRemark(reviewRemark);
        noteBasicMapper.updateByPrimaryKeySelective(update);
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

    private boolean shouldUploadAsRagentDocument(Byte type) {
        return type != null && type != 0 && type != 1 && type != 2;
    }

    private record ReviewUploadPayload(Long noteId, String title, String content, Byte type, Long authorId) {
    }
}
