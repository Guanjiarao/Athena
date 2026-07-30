package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DigitalAssetFeedbackQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String auditStatus;

    private Long userId;

    private Integer current = 1;

    private Integer size = 10;
}
