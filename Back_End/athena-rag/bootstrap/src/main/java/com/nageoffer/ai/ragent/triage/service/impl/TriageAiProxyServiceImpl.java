

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.triage.controller.request.ClientChatCompletionRequest;
import com.nageoffer.ai.ragent.triage.controller.request.VisionAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.ClientChatCompletionResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.VisionAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.service.TriageAiProxyService;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageAiProxyServiceImpl implements TriageAiProxyService {

    private final TriageModelGateway triageModelGateway;
    private final ObjectMapper objectMapper;

    @Value("${ai.providers.bailian.url:}")
    private String bailianBaseUrl;

    @Value("${ai.providers.bailian.api-key:}")
    private String bailianApiKey;

    @Value("${ai.providers.bailian.endpoints.chat:}")
    private String bailianChatEndpoint;

    @Override
    public ClientChatCompletionResponse complete(ClientChatCompletionRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new ClientException("messages 不能为空");
        }
        List<ChatMessage> messages = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getJsonMode())) {
            messages.add(ChatMessage.system("请严格返回合法 JSON，不要输出 Markdown 代码块，不要输出额外解释。"));
        }
        for (ClientChatCompletionRequest.ClientChatMessage each : request.getMessages()) {
            if (each == null || StrUtil.isBlank(each.getContent())) {
                continue;
            }
            messages.add(convertMessage(each));
        }
        if (messages.isEmpty()) {
            throw new ClientException("messages 中缺少有效内容");
        }
        String content = triageModelGateway.chatWithTextModel(
                messages,
                Boolean.TRUE.equals(request.getJsonMode()) ? 0.1D : 0.7D,
                Boolean.TRUE.equals(request.getJsonMode()) ? 0.3D : null,
                1200
        );
        return ClientChatCompletionResponse.builder()
                .content(StrUtil.blankToDefault(content, ""))
                .model(StrUtil.blankToDefault(request.getModel(), "triage-text"))
                .build();
    }

    @Override
    public VisionAnalyzeResponse analyzeVision(VisionAnalyzeRequest request) {
        if (request == null) {
            throw new ClientException("视觉分析请求不能为空");
        }
        if (StrUtil.isBlank(request.getImageUrl()) || StrUtil.isBlank(request.getPrompt())) {
            throw new ClientException("imageUrl 和 prompt 不能为空");
        }
        if (StrUtil.isBlank(bailianApiKey)) {
            throw new ServiceException("后端未配置视觉模型 API Key");
        }
        try {
            String requestBody = buildVisionRequestBody(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBailianChatUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + bailianApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceException("视觉分析代理调用失败，HTTP " + response.statusCode());
            }
            return VisionAnalyzeResponse.builder()
                    .content(extractVisionContent(response.body()))
                    .model(triageModelGateway.resolveVisionModel(request.getModel()))
                    .build();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("视觉分析代理调用失败", ex);
            throw new ServiceException("视觉分析代理调用失败：" + ex.getMessage());
        }
    }

    private ChatMessage convertMessage(ClientChatCompletionRequest.ClientChatMessage message) {
        String role = StrUtil.blankToDefault(message.getRole(), "user").trim().toLowerCase();
        return switch (role) {
            case "system" -> ChatMessage.system(message.getContent());
            case "assistant" -> ChatMessage.assistant(message.getContent());
            default -> ChatMessage.user(message.getContent());
        };
    }

    private String buildVisionRequestBody(VisionAnalyzeRequest request) throws IOException {
        String model = triageModelGateway.resolveVisionModel(request.getModel());
        JsonNode body = objectMapper.createObjectNode()
                .put("model", model)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .set("content", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("type", "image_url")
                                                .set("image_url", objectMapper.createObjectNode().put("url", request.getImageUrl())))
                                        .add(objectMapper.createObjectNode().put("type", "text").put("text", request.getPrompt())))));
        return objectMapper.writeValueAsString(body);
    }

    private String extractVisionContent(String rawBody) throws IOException {
        JsonNode root = objectMapper.readTree(rawBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ServiceException("视觉分析响应格式异常：缺少 choices");
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (StrUtil.isBlank(content)) {
            throw new ServiceException("视觉分析响应格式异常：缺少 content");
        }
        return content;
    }

    private String resolveBailianChatUrl() {
        if (StrUtil.isBlank(bailianBaseUrl) || StrUtil.isBlank(bailianChatEndpoint)) {
            throw new ServiceException("后端未配置千问视觉服务地址");
        }
        String normalizedBase = StrUtil.removeSuffix(bailianBaseUrl.trim(), "/");
        String normalizedEndpoint = bailianChatEndpoint.startsWith("/") ? bailianChatEndpoint : "/" + bailianChatEndpoint;
        return normalizedBase + normalizedEndpoint;
    }
}