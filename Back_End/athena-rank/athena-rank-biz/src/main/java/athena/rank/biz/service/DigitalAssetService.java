package athena.rank.biz.service;

import athena.rank.api.dto.DigitalAssetAccountDTO;
import athena.rank.api.dto.DigitalAssetFeedbackAuditDTO;
import athena.rank.api.dto.DigitalAssetFeedbackDTO;
import athena.rank.api.dto.DigitalAssetFeedbackPageDTO;
import athena.rank.api.dto.DigitalAssetFeedbackQueryDTO;
import athena.rank.api.dto.DigitalAssetFeedbackSubmitDTO;

public interface DigitalAssetService {

    DigitalAssetFeedbackDTO submitFeedback(DigitalAssetFeedbackSubmitDTO submitDTO);

    DigitalAssetFeedbackPageDTO myFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO);

    DigitalAssetAccountDTO myAccount();

    DigitalAssetFeedbackPageDTO adminFeedbacks(DigitalAssetFeedbackQueryDTO queryDTO);

    DigitalAssetFeedbackDTO approve(Long id, DigitalAssetFeedbackAuditDTO auditDTO);

    DigitalAssetFeedbackDTO reject(Long id, DigitalAssetFeedbackAuditDTO auditDTO);
}
