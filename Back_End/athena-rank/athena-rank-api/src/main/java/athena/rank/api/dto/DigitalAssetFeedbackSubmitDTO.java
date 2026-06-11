package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DigitalAssetFeedbackSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;

    private String messageId;

    /** 反馈值：1=点赞，-1=点踩 */
    private Integer vote;

    private String reason;

    private String comment;
}
