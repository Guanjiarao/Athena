package athena.insight.biz.service.impl;

import athena.insight.biz.config.AiReportProperties;
import athena.insight.biz.domain.vo.RecommendItemVO;
import athena.insight.biz.domain.vo.UserAnalysisReportVO;
import athena.insight.biz.service.AiReportNarrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportNarrationServiceImpl implements AiReportNarrationService {

    private final AiReportProperties aiReportProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    @Override
    public String generateSummary(UserAnalysisReportVO reportVO, String fallbackSummary) {
        if (!aiReportProperties.isEnabled()) {
            return fallbackSummary;
        }
        if (reportVO == null || !hasEnoughSignal(reportVO)) {
            return fallbackSummary;
        }
        if (!StringUtils.hasText(aiReportProperties.getUrl())
                || !StringUtils.hasText(aiReportProperties.getApiKey())
                || !StringUtils.hasText(aiReportProperties.getModel())) {
            log.warn("[AiReport] AI 报告配置不完整，降级使用规则摘要");
            return fallbackSummary;
        }

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofMillis(aiReportProperties.getConnectTimeoutMs()))
                    .setReadTimeout(Duration.ofMillis(aiReportProperties.getReadTimeoutMs()))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiReportProperties.getApiKey());

            ObjectNode requestBody = buildRequestBody(reportVO, fallbackSummary);
            ResponseEntity<String> response = restTemplate.exchange(
                    aiReportProperties.getUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody.toString(), headers),
                    String.class
            );
            String summary = extractContent(response.getBody());
            if (!StringUtils.hasText(summary)) {
                return fallbackSummary;
            }
            summary = summary.trim();
            if (aiReportProperties.isAppendDisclaimer() && StringUtils.hasText(aiReportProperties.getDisclaimer())) {
                summary = summary + "\n\n" + aiReportProperties.getDisclaimer().trim();
            }
            return summary;
        } catch (Exception e) {
            log.warn("[AiReport] AI 报告生成失败，降级使用规则摘要", e);
            return fallbackSummary;
        }
    }

    private boolean hasEnoughSignal(UserAnalysisReportVO reportVO) {
        return !CollectionUtils.isEmpty(reportVO.getHealthFocuses())
                || !CollectionUtils.isEmpty(reportVO.getContentFocuses())
                || !CollectionUtils.isEmpty(reportVO.getRiskTags())
                || !CollectionUtils.isEmpty(reportVO.getRecommendTopics())
                || !CollectionUtils.isEmpty(reportVO.getReadingSuggestions());
    }

    private ObjectNode buildRequestBody(UserAnalysisReportVO reportVO, String fallbackSummary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", aiReportProperties.getModel());
        root.put("temperature", aiReportProperties.getTemperature());
        root.put("max_tokens", aiReportProperties.getMaxTokens());

        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", buildSystemPrompt());
        messages.addObject()
                .put("role", "user")
                .put("content", buildUserPrompt(reportVO, fallbackSummary));
        return root;
    }

    private String buildSystemPrompt() {
        return "你是女性健康与生活方式分析助手。你的任务是基于给定的结构化报告数据，生成一段温和、专业、克制、有陪伴感的中文报告摘要。"
                + "只允许基于输入数据表达，不要编造事实，不要做疾病诊断，不要使用恐吓语气。"
                + "优先总结当前关注重点、近期内容偏好、轻量可执行建议。"
                + "如果数据有限，请自然说明“目前数据还有限”，但不要生硬。"
                + "直接输出摘要正文，不要使用 markdown 标题，不要输出 JSON，不要出现“作为 AI”之类措辞。";
    }

    private String buildUserPrompt(UserAnalysisReportVO reportVO, String fallbackSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下结构化报告数据，生成 2 到 3 段、约 120 到 220 字的用户分析摘要。\n");
        sb.append("已有规则摘要：").append(defaultText(fallbackSummary)).append("\n");
        sb.append("currentModeType=").append(reportVO.getCurrentModeType()).append("\n");
        sb.append("averageCycleLength=").append(reportVO.getAverageCycleLength()).append("\n");
        sb.append("averageDurationDays=").append(reportVO.getAverageDurationDays()).append("\n");
        sb.append("healthFocuses=").append(defaultList(reportVO.getHealthFocuses())).append("\n");
        sb.append("contentFocuses=").append(defaultList(reportVO.getContentFocuses())).append("\n");
        sb.append("riskTags=").append(defaultList(reportVO.getRiskTags())).append("\n");
        sb.append("recommendTopics=").append(defaultList(reportVO.getRecommendTopics())).append("\n");
        sb.append("readingSuggestions=").append(defaultReadingSuggestions(reportVO.getReadingSuggestions())).append("\n");
        sb.append("要求：不要夸大风险，不要给出诊断结论，结尾给出轻量建议。\n");
        return sb.toString();
    }

    private String extractContent(String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode first = choices.get(0);
        JsonNode content = first.path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText();
    }

    private String defaultList(List<String> values) {
        return CollectionUtils.isEmpty(values) ? "[]" : values.toString();
    }

    private String defaultReadingSuggestions(List<RecommendItemVO> items) {
        if (CollectionUtils.isEmpty(items)) {
            return "[]";
        }
        return items.stream()
                .map(RecommendItemVO::getTitle)
                .filter(StringUtils::hasText)
                .toList()
                .toString();
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text : "暂无";
    }
}
