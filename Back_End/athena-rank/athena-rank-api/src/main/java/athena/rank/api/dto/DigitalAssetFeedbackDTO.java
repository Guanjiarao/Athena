package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DigitalAssetFeedbackDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String conversationId;

    private String messageId;

    private Integer vote;

    private String reason;

    private String comment;

    private String ragMessageRole;

    private String ragMessageContent;

    private String ragThinkingContent;

    private Integer ragThinkingDuration;

    private LocalDateTime ragMessageCreateTime;

    private String auditStatus;

    private Long auditUserId;

    private LocalDateTime auditTime;

    private String auditRemark;

    private Integer assetScore;

    private Long assetRecordId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
