package athena.rank.biz.client.impl;

import athena.athenaframework.utils.GlobalConstants;
import athena.rank.biz.client.AthenaRagConversationClient;
import athena.rank.biz.client.dto.RagConversationCheckResult;
import athena.rank.biz.client.dto.RagConversationMessageDTO;
import athena.rank.biz.config.AthenaRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AthenaRagConversationClientImpl implements AthenaRagConversationClient {

    private static final String RAG_SUCCESS_CODE = "0";
    private static final String ASSISTANT_ROLE = "assistant";

    private final RestTemplate restTemplate;
    private final AthenaRagProperties ragProperties;

    @Override
    public RagConversationMessageDTO getMessage(Long userId, String conversationId, String messageId) {
        validate(userId, conversationId, messageId);
        List<RagConversationMessageDTO> messages = listMessages(userId, conversationId);
        return messages.stream()
                .filter(message -> Objects.equals(messageId, message.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("消息不存在或不属于当前用户"));
    }

    @Override
    public RagConversationCheckResult checkAssistantMessage(Long userId, String conversationId, String messageId) {
        RagConversationMessageDTO message = getMessage(userId, conversationId, messageId);
        if (!ASSISTANT_ROLE.equalsIgnoreCase(message.getRole())) {
            throw new IllegalArgumentException("仅支持对 AI 回答提交反馈");
        }
        RagConversationCheckResult result = new RagConversationCheckResult();
        result.setConversationId(message.getConversationId());
        result.setMessageId(message.getId());
        result.setRole(message.getRole());
        result.setContent(message.getContent());
        result.setThinkingContent(message.getThinkingContent());
        result.setThinkingDuration(message.getThinkingDuration());
        result.setMessageCreateTime(message.getCreateTime());
        return result;
    }

    private List<RagConversationMessageDTO> listMessages(Long userId, String conversationId) {
        String url = ragProperties.getBaseUrl() + "/api/ragent/conversations/" + conversationId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.set(GlobalConstants.USER_ID, String.valueOf(userId));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> payload = response.getBody();
            if (payload == null) {
                throw new IllegalStateException("RAG 服务返回为空");
            }
            String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
            String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
            if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
                throw new IllegalStateException("调用 RAG 会话消息接口失败：" + message);
            }
            Object data = payload.get("data");
            if (!(data instanceof List<?> rawList) || CollectionUtils.isEmpty(rawList)) {
                return List.of();
            }
            return rawList.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> toMessageDTO((Map<?, ?>) item))
                    .toList();
        } catch (RestClientException ex) {
            log.warn("调用 RAG 会话消息接口异常, userId={}, conversationId={}", userId, conversationId, ex);
            throw new IllegalStateException("RAG 服务暂不可用，请稍后重试");
        }
    }

    private RagConversationMessageDTO toMessageDTO(Map<?, ?> raw) {
        RagConversationMessageDTO dto = new RagConversationMessageDTO();
        dto.setId(asString(raw.get("id")));
        dto.setConversationId(asString(raw.get("conversationId")));
        dto.setRole(asString(raw.get("role")));
        dto.setContent(asString(raw.get("content")));
        dto.setThinkingContent(asString(raw.get("thinkingContent")));
        dto.setThinkingDuration(asInteger(raw.get("thinkingDuration")));
        dto.setVote(asInteger(raw.get("vote")));
        dto.setCreateTime(asLocalDateTime(raw.get("createTime")));
        return dto;
    }

    private void validate(Long userId, String conversationId, String messageId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("会话 ID 不能为空");
        }
        if (!StringUtils.hasText(messageId)) {
            throw new IllegalArgumentException("消息 ID 不能为空");
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private java.time.LocalDateTime asLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return java.time.LocalDateTime.parse(text.replace(" ", "T").replace("Z", ""));
        }
        return null;
    }
}
