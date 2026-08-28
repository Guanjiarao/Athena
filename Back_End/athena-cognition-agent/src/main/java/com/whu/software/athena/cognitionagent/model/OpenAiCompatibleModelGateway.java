package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProperties;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** OpenAI-compatible transport shared by all cognition nodes. */
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private final ObjectMapper mapper;
    private final IntentModelProperties properties;
    private final HttpClient client;

    public OpenAiCompatibleModelGateway(ObjectMapper mapper, IntentModelProperties properties) {
        this(mapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    public OpenAiCompatibleModelGateway(ObjectMapper mapper,
                                        IntentModelProperties properties,
                                        HttpClient client) {
        this.mapper = mapper;
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        if (request == null || blank(request.systemPrompt()) || blank(request.userPrompt())) {
            throw new IllegalArgumentException("model request and prompts are required");
        }
        properties.validateForRealProvider();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpointUri())
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(request)))
                    .build();
            HttpResponse<String> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 408
                        || response.statusCode() == 429 || response.statusCode() >= 500;
                throw new IntentModelProviderException(AgentErrorCode.MODEL_UNAVAILABLE,
                        "model provider returned HTTP " + response.statusCode(), retryable);
            }
            return parseResponse(response.body());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new IntentModelProviderException(
                    AgentErrorCode.MODEL_TIMEOUT, "model provider request timed out", true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntentModelProviderException(
                    AgentErrorCode.MODEL_TIMEOUT, "model provider request was interrupted", true);
        } catch (IOException exception) {
            throw new IntentModelProviderException(
                    AgentErrorCode.MODEL_UNAVAILABLE, "model provider is unavailable", true);
        }
    }

    private String requestBody(ModelRequest request) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.getModelName());
        body.put("temperature", 0);
        body.put("max_tokens", Math.max(64, request.maxTokens()));
        if (properties.isJsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content",
                "promptVersion=" + request.promptVersion() + "\n" + request.userPrompt());
        return mapper.writeValueAsString(body);
    }

    private ModelResponse parseResponse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (blank(content)) {
                throw invalidOutput("model response has no choices[0].message.content");
            }
            JsonNode output = mapper.readTree(content);
            if (!output.isObject()) {
                throw invalidOutput("model output must be a JSON object");
            }
            JsonNode usage = root.path("usage");
            Integer input = integerOrNull(usage.get("prompt_tokens"));
            Integer outputTokens = integerOrNull(usage.get("completion_tokens"));
            Integer total = integerOrNull(usage.get("total_tokens"));
            return new ModelResponse(providerName(), modelName(), output, input, outputTokens,
                    total, estimatedCost(input, outputTokens));
        } catch (IntentModelProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidOutput("model response is not valid JSON");
        }
    }

    private URI endpointUri() {
        String base = properties.getBaseUrl().trim();
        String path = properties.getEndpointPath() == null ? "" : properties.getEndpointPath().trim();
        if (base.endsWith("/") && path.startsWith("/")) {
            path = path.substring(1);
        } else if (!base.endsWith("/") && !path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(base + path);
    }

    private Integer integerOrNull(JsonNode value) {
        return value != null && value.isIntegralNumber() ? value.intValue() : null;
    }

    private Double estimatedCost(Integer input, Integer output) {
        if (input == null || output == null
                || properties.getInputCostPerMillionTokens() == null
                || properties.getOutputCostPerMillionTokens() == null) {
            return null;
        }
        return input * properties.getInputCostPerMillionTokens() / 1_000_000d
                + output * properties.getOutputCostPerMillionTokens() / 1_000_000d;
    }

    private IntentModelProviderException invalidOutput(String message) {
        return new IntentModelProviderException(
                AgentErrorCode.MODEL_OUTPUT_INVALID, message, false);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
