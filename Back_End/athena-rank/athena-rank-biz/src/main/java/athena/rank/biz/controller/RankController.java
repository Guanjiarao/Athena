package athena.rank.biz.controller;

import athena.athenaframework.result.Result;
import athena.rank.api.RankFeignApi;
import athena.rank.api.dto.DigitalAssetAccountDTO;
import athena.rank.api.dto.DigitalAssetFeedbackAuditDTO;
import athena.rank.api.dto.DigitalAssetFeedbackDTO;
import athena.rank.api.dto.DigitalAssetFeedbackPageDTO;
import athena.rank.api.dto.DigitalAssetFeedbackQueryDTO;
import athena.rank.api.dto.DigitalAssetFeedbackSubmitDTO;
import athena.rank.api.dto.RankListDTO;
import athena.rank.api.dto.RankPositionDTO;
import athena.rank.api.dto.RankQueryDTO;
import athena.rank.api.dto.RankUpdateDTO;
import athena.rank.biz.service.DigitalAssetService;
import athena.rank.biz.service.RankService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RankController implements RankFeignApi {

    @Resource
    private RankService rankService;

    @Resource
    private DigitalAssetService digitalAssetService;

    @Override
    public Result<?> update(RankUpdateDTO updateDTO) {
        rankService.update(updateDTO);
        return Result.ok();
    }

    @Override
    public Result<RankListDTO> top(RankQueryDTO queryDTO) {
        return Result.ok(rankService.top(queryDTO));
    }

    @Override
    public Result<RankPositionDTO> position(String scene, Long memberId, Long periodTimeMillis) {
        return Result.ok(rankService.position(scene, memberId, periodTimeMillis));
    }

    @Override
    public Result<DigitalAssetFeedbackDTO> submitDigitalAssetFeedback(DigitalAssetFeedbackSubmitDTO submitDTO) {
        return Result.ok(digitalAssetService.submitFeedback(submitDTO));
    }

    @Override
    public Result<DigitalAssetFeedbackPageDTO> myDigitalAssetFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO) {
        return Result.ok(digitalAssetService.myFeedbacks(queryDTO));
    }

    @Override
    public Result<DigitalAssetAccountDTO> myDigitalAssetAccount() {
        return Result.ok(digitalAssetService.myAccount());
    }

    @Override
    public Result<DigitalAssetFeedbackPageDTO> adminDigitalAssetFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO) {
        return Result.ok(digitalAssetService.adminFeedbacks(queryDTO));
    }

    @Override
    public Result<DigitalAssetFeedbackDTO> approveDigitalAssetFeedback(Long id, DigitalAssetFeedbackAuditDTO auditDTO) {
        return Result.ok(digitalAssetService.approve(id, auditDTO));
    }

    @Override
    public Result<DigitalAssetFeedbackDTO> rejectDigitalAssetFeedback(Long id, DigitalAssetFeedbackAuditDTO auditDTO) {
        return Result.ok(digitalAssetService.reject(id, auditDTO));
    }
}
