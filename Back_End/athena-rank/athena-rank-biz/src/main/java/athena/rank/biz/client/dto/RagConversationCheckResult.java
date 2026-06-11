package athena.rank.biz.client.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagConversationCheckResult {

    private String conversationId;

    private String messageId;

    private String role;

    private String content;

    private String thinkingContent;

    private Integer thinkingDuration;

    private LocalDateTime messageCreateTime;
}
