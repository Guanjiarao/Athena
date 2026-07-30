package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DigitalAssetFeedbackAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核通过时必填，范围 1~100 */
    private Integer assetScore;

    private String auditRemark;
}
