package athena.rank.biz.client.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagConversationMessageDTO {

    private String id;

    private String conversationId;

    private String role;

    private String content;

    private String thinkingContent;

    private Integer thinkingDuration;

    private Integer vote;

    private LocalDateTime createTime;
}
