package athena.rank.biz.service.impl;

import athena.athenaframework.utils.GlobalConstants;
import athena.rank.api.constant.RankSceneConstants;
import athena.rank.api.dto.DigitalAssetAccountDTO;
import athena.rank.api.dto.DigitalAssetFeedbackAuditDTO;
import athena.rank.api.dto.DigitalAssetFeedbackDTO;
import athena.rank.api.dto.DigitalAssetFeedbackPageDTO;
import athena.rank.api.dto.DigitalAssetFeedbackQueryDTO;
import athena.rank.api.dto.DigitalAssetFeedbackSubmitDTO;
import athena.rank.api.dto.RankUpdateDTO;
import athena.rank.biz.client.AthenaRagConversationClient;
import athena.rank.biz.client.dto.RagConversationCheckResult;
import athena.rank.biz.config.RankProperties;
import athena.rank.biz.constant.DigitalAssetConstants;
import athena.rank.biz.domain.DigitalAssetAccountDO;
import athena.rank.biz.domain.DigitalAssetFeedbackDO;
import athena.rank.biz.domain.DigitalAssetRecordDO;
import athena.rank.biz.mapper.DigitalAssetAccountMapper;
import athena.rank.biz.mapper.DigitalAssetFeedbackMapper;
import athena.rank.biz.mapper.DigitalAssetRecordMapper;
import athena.rank.biz.service.DigitalAssetService;
import athena.rank.biz.service.RankService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DigitalAssetServiceImpl implements DigitalAssetService {

    private final DigitalAssetFeedbackMapper feedbackMapper;
    private final DigitalAssetAccountMapper accountMapper;
    private final DigitalAssetRecordMapper recordMapper;
    private final AthenaRagConversationClient ragConversationClient;
    private final RankService rankService;
    private final RankProperties rankProperties;
    private final HttpServletRequest request;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DigitalAssetFeedbackDTO submitFeedback(DigitalAssetFeedbackSubmitDTO submitDTO) {
        Long userId = currentUserId();
        validateSubmit(submitDTO);
        RagConversationCheckResult ragMessage = ragConversationClient.checkAssistantMessage(
                userId,
                submitDTO.getConversationId(),
                submitDTO.getMessageId());

        DigitalAssetFeedbackDO existing = feedbackMapper.selectOne(
                new LambdaQueryWrapper<DigitalAssetFeedbackDO>()
                        .eq(DigitalAssetFeedbackDO::getUserId, userId)
                        .eq(DigitalAssetFeedbackDO::getMessageId, submitDTO.getMessageId())
                        .eq(DigitalAssetFeedbackDO::getDeleted, 0)
        );
        if (existing != null && !DigitalAssetConstants.AUDIT_STATUS_PENDING.equals(existing.getAuditStatus())) {
            throw new IllegalStateException("该反馈已审核，不能重复提交");
        }

        DigitalAssetFeedbackDO feedback = existing == null ? new DigitalAssetFeedbackDO() : existing;
        feedback.setUserId(userId);
        feedback.setConversationId(submitDTO.getConversationId());
        feedback.setMessageId(submitDTO.getMessageId());
        feedback.setVote(submitDTO.getVote());
        feedback.setReason(submitDTO.getReason());
        feedback.setComment(submitDTO.getComment());
        feedback.setRagMessageRole(ragMessage.getRole());
        feedback.setRagMessageContent(ragMessage.getContent());
        feedback.setRagThinkingContent(ragMessage.getThinkingContent());
        feedback.setRagThinkingDuration(ragMessage.getThinkingDuration());
        feedback.setRagMessageCreateTime(ragMessage.getMessageCreateTime());
        feedback.setAuditStatus(DigitalAssetConstants.AUDIT_STATUS_PENDING);
        feedback.setAuditUserId(null);
        feedback.setAuditTime(null);
        feedback.setAuditRemark(null);
        feedback.setAssetScore(null);
        feedback.setAssetRecordId(null);
        if (existing == null) {
            feedbackMapper.insert(feedback);
        } else {
            feedbackMapper.updateById(feedback);
        }
        return toFeedbackDTO(feedback);
    }

    @Override
    public DigitalAssetFeedbackPageDTO myFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO) {
        DigitalAssetFeedbackQueryDTO query = normalizeQuery(queryDTO);
        query.setUserId(currentUserId());
        return pageFeedbacks(query);
    }

    @Override
    public DigitalAssetAccountDTO myAccount() {
        Long userId = currentUserId();
        DigitalAssetAccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<DigitalAssetAccountDO>()
                        .eq(DigitalAssetAccountDO::getUserId, userId)
                        .eq(DigitalAssetAccountDO::getDeleted, 0)
        );
        DigitalAssetAccountDTO dto = new DigitalAssetAccountDTO();
        dto.setUserId(userId);
        dto.setTotalAsset(account == null ? 0 : account.getTotalAsset());
        dto.setUpdateTime(account == null ? null : account.getUpdateTime());
        return dto;
    }

    @Override
    public DigitalAssetFeedbackPageDTO adminFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO) {
        return pageFeedbacks(normalizeQuery(queryDTO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DigitalAssetFeedbackDTO approve(Long id, DigitalAssetFeedbackAuditDTO auditDTO) {
        Long auditUserId = currentUserId();
        if (id == null) {
            throw new IllegalArgumentException("反馈申请 ID 不能为空");
        }
        if (auditDTO == null || auditDTO.getAssetScore() == null) {
            throw new IllegalArgumentException("数字资产数量不能为空");
        }
        int assetScore = auditDTO.getAssetScore();
        int minScore = rankProperties.getDigitalAsset().getMinScore();
        int maxScore = rankProperties.getDigitalAsset().getMaxScore();
        if (assetScore < minScore || assetScore > maxScore) {
            throw new IllegalArgumentException("数字资产数量必须在 " + minScore + "~" + maxScore + " 之间");
        }
        DigitalAssetFeedbackDO feedback = loadPendingFeedback(id);
        DigitalAssetAccountDO account = getOrCreateAccount(feedback.getUserId());
        int balanceAfter = account.getTotalAsset() + assetScore;
        account.setTotalAsset(balanceAfter);
        accountMapper.updateById(account);

        DigitalAssetRecordDO record = new DigitalAssetRecordDO();
        record.setUserId(feedback.getUserId());
        record.setSourceType(DigitalAssetConstants.SOURCE_TYPE_RAG_FEEDBACK);
        record.setSourceId(feedback.getId());
        record.setChangeAmount(assetScore);
        record.setBalanceAfter(balanceAfter);
        record.setRemark(auditDTO.getAuditRemark());
        recordMapper.insert(record);

        feedback.setAuditStatus(DigitalAssetConstants.AUDIT_STATUS_APPROVED);
        feedback.setAuditUserId(auditUserId);
        feedback.setAuditTime(LocalDateTime.now());
        feedback.setAuditRemark(auditDTO.getAuditRemark());
        feedback.setAssetScore(assetScore);
        feedback.setAssetRecordId(record.getId());
        feedbackMapper.updateById(feedback);

        RankUpdateDTO rankUpdateDTO = new RankUpdateDTO();
        rankUpdateDTO.setScene(RankSceneConstants.DATA_ASSET_DAILY);
        rankUpdateDTO.setMemberId(feedback.getUserId());
        rankUpdateDTO.setDelta((long) assetScore);
        rankUpdateDTO.setRequestId("digital_asset_feedback:" + feedback.getId());
        rankUpdateDTO.setEventTimeMillis(System.currentTimeMillis());
        rankService.update(rankUpdateDTO);

        return toFeedbackDTO(feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DigitalAssetFeedbackDTO reject(Long id, DigitalAssetFeedbackAuditDTO auditDTO) {
        Long auditUserId = currentUserId();
        if (id == null) {
            throw new IllegalArgumentException("反馈申请 ID 不能为空");
        }
        DigitalAssetFeedbackDO feedback = loadPendingFeedback(id);
        feedback.setAuditStatus(DigitalAssetConstants.AUDIT_STATUS_REJECTED);
        feedback.setAuditUserId(auditUserId);
        feedback.setAuditTime(LocalDateTime.now());
        feedback.setAuditRemark(auditDTO == null ? null : auditDTO.getAuditRemark());
        feedbackMapper.updateById(feedback);
        return toFeedbackDTO(feedback);
    }

    private DigitalAssetFeedbackPageDTO pageFeedbacks(DigitalAssetFeedbackQueryDTO query) {
        LambdaQueryWrapper<DigitalAssetFeedbackDO> wrapper = new LambdaQueryWrapper<DigitalAssetFeedbackDO>()
                .eq(DigitalAssetFeedbackDO::getDeleted, 0)
                .eq(query.getUserId() != null, DigitalAssetFeedbackDO::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getAuditStatus()), DigitalAssetFeedbackDO::getAuditStatus, query.getAuditStatus())
                .orderByDesc(DigitalAssetFeedbackDO::getCreateTime);
        Page<DigitalAssetFeedbackDO> page = feedbackMapper.selectPage(Page.of(query.getCurrent(), query.getSize()), wrapper);
        DigitalAssetFeedbackPageDTO result = new DigitalAssetFeedbackPageDTO();
        result.setCurrent(query.getCurrent());
        result.setSize(query.getSize());
        result.setTotal(page.getTotal());
        List<DigitalAssetFeedbackDTO> records = page.getRecords().stream()
                .map(this::toFeedbackDTO)
                .toList();
        result.setRecords(records);
        return result;
    }

    private DigitalAssetFeedbackQueryDTO normalizeQuery(DigitalAssetFeedbackQueryDTO queryDTO) {
        DigitalAssetFeedbackQueryDTO query = queryDTO == null ? new DigitalAssetFeedbackQueryDTO() : queryDTO;
        if (query.getCurrent() == null || query.getCurrent() < 1) {
            query.setCurrent(1);
        }
        if (query.getSize() == null || query.getSize() < 1) {
            query.setSize(10);
        }
        if (query.getSize() > 100) {
            query.setSize(100);
        }
        return query;
    }

    private DigitalAssetFeedbackDO loadPendingFeedback(Long id) {
        DigitalAssetFeedbackDO feedback = feedbackMapper.selectById(id);
        if (feedback == null || Integer.valueOf(1).equals(feedback.getDeleted())) {
            throw new IllegalArgumentException("反馈申请不存在");
        }
        if (!DigitalAssetConstants.AUDIT_STATUS_PENDING.equals(feedback.getAuditStatus())) {
            throw new IllegalStateException("仅待审核申请可以操作");
        }
        return feedback;
    }

    private DigitalAssetAccountDO getOrCreateAccount(Long userId) {
        DigitalAssetAccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<DigitalAssetAccountDO>()
                        .eq(DigitalAssetAccountDO::getUserId, userId)
                        .eq(DigitalAssetAccountDO::getDeleted, 0)
        );
        if (account != null) {
            return account;
        }
        DigitalAssetAccountDO created = new DigitalAssetAccountDO();
        created.setUserId(userId);
        created.setTotalAsset(0);
        accountMapper.insert(created);
        return created;
    }

    private void validateSubmit(DigitalAssetFeedbackSubmitDTO submitDTO) {
        if (submitDTO == null) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }
        if (!StringUtils.hasText(submitDTO.getConversationId())) {
            throw new IllegalArgumentException("会话 ID 不能为空");
        }
        if (!StringUtils.hasText(submitDTO.getMessageId())) {
            throw new IllegalArgumentException("消息 ID 不能为空");
        }
        Integer vote = submitDTO.getVote();
        if (vote == null || (vote != 1 && vote != -1)) {
            throw new IllegalArgumentException("反馈值必须为 1 或 -1");
        }
    }

    private Long currentUserId() {
        String userId = request.getHeader(GlobalConstants.USER_ID);
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("未获取到当前登录用户");
        }
        return Long.valueOf(userId);
    }

    private DigitalAssetFeedbackDTO toFeedbackDTO(DigitalAssetFeedbackDO source) {
        DigitalAssetFeedbackDTO dto = new DigitalAssetFeedbackDTO();
        dto.setId(source.getId());
        dto.setUserId(source.getUserId());
        dto.setConversationId(source.getConversationId());
        dto.setMessageId(source.getMessageId());
        dto.setVote(source.getVote());
        dto.setReason(source.getReason());
        dto.setComment(source.getComment());
        dto.setRagMessageRole(source.getRagMessageRole());
        dto.setRagMessageContent(source.getRagMessageContent());
        dto.setRagThinkingContent(source.getRagThinkingContent());
        dto.setRagThinkingDuration(source.getRagThinkingDuration());
        dto.setRagMessageCreateTime(source.getRagMessageCreateTime());
        dto.setAuditStatus(source.getAuditStatus());
        dto.setAuditUserId(source.getAuditUserId());
        dto.setAuditTime(source.getAuditTime());
        dto.setAuditRemark(source.getAuditRemark());
        dto.setAssetScore(source.getAssetScore());
        dto.setAssetRecordId(source.getAssetRecordId());
        dto.setCreateTime(source.getCreateTime());
        dto.setUpdateTime(source.getUpdateTime());
        return dto;
    }
}
