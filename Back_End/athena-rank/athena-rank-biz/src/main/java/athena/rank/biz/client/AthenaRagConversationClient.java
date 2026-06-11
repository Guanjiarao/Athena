package athena.rank.biz.client;

import athena.rank.biz.client.dto.RagConversationCheckResult;
import athena.rank.biz.client.dto.RagConversationMessageDTO;

public interface AthenaRagConversationClient {

    RagConversationMessageDTO getMessage(Long userId, String conversationId, String messageId);

    RagConversationCheckResult checkAssistantMessage(Long userId, String conversationId, String messageId);
}
