package athena.rank.api;

import athena.athenaframework.result.Result;
import athena.rank.api.constant.RankApiConstants;
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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = RankApiConstants.SERVICE_NAME)
public interface RankFeignApi {

    String PREFIX = "/athena/rank";

    @PostMapping(PREFIX + "/update")
    Result<?> update(@RequestBody RankUpdateDTO updateDTO);

    @PostMapping(PREFIX + "/top")
    Result<RankListDTO> top(@RequestBody RankQueryDTO queryDTO);

    @GetMapping(PREFIX + "/position")
    Result<RankPositionDTO> position(@RequestParam String scene,
                                     @RequestParam Long memberId,
                                     @RequestParam(required = false) Long periodTimeMillis);

    @PostMapping(PREFIX + "/digital-assets/feedbacks")
    Result<DigitalAssetFeedbackDTO> submitDigitalAssetFeedback(@RequestBody DigitalAssetFeedbackSubmitDTO submitDTO);

    @PostMapping(PREFIX + "/digital-assets/feedbacks/my")
    Result<DigitalAssetFeedbackPageDTO> myDigitalAssetFeedbacks(@RequestBody DigitalAssetFeedbackQueryDTO queryDTO);

    @GetMapping(PREFIX + "/digital-assets/account")
    Result<DigitalAssetAccountDTO> myDigitalAssetAccount();

    @PostMapping(PREFIX + "/admin/digital-assets/feedbacks")
    Result<DigitalAssetFeedbackPageDTO> adminDigitalAssetFeedbacks(@RequestBody DigitalAssetFeedbackQueryDTO queryDTO);

    @PostMapping(PREFIX + "/admin/digital-assets/feedbacks/{id}/approve")
    Result<DigitalAssetFeedbackDTO> approveDigitalAssetFeedback(@PathVariable Long id,
                                                                @RequestBody DigitalAssetFeedbackAuditDTO auditDTO);

    @PostMapping(PREFIX + "/admin/digital-assets/feedbacks/{id}/reject")
    Result<DigitalAssetFeedbackDTO> rejectDigitalAssetFeedback(@PathVariable Long id,
                                                               @RequestBody DigitalAssetFeedbackAuditDTO auditDTO);
}
