package com.whu.software.athena.cognitionagent.intent.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration read from environment-backed Spring properties. */
@ConfigurationProperties(prefix = "athena.model")
public class IntentModelProperties {

    private String provider = "mock";
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private String endpointPath = "/v1/chat/completions";
    private long timeoutMs = 15000;
    private boolean jsonMode = true;
    private Double inputCostPerMillionTokens;
    private Double outputCostPerMillionTokens;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isJsonMode() { return jsonMode; }
    public void setJsonMode(boolean jsonMode) { this.jsonMode = jsonMode; }
    public Double getInputCostPerMillionTokens() { return inputCostPerMillionTokens; }
    public void setInputCostPerMillionTokens(Double value) { this.inputCostPerMillionTokens = value; }
    public Double getOutputCostPerMillionTokens() { return outputCostPerMillionTokens; }
    public void setOutputCostPerMillionTokens(Double value) { this.outputCostPerMillionTokens = value; }

    public void validateForRealProvider() {
        if (isBlank(apiKey) || isBlank(baseUrl) || isBlank(modelName)) {
            throw new IllegalStateException(
                    "ATHENA_MODEL_API_KEY, ATHENA_MODEL_BASE_URL and ATHENA_MODEL_NAME are required");
        }
        if (timeoutMs < 1000 || timeoutMs > 120000) {
            throw new IllegalStateException("ATHENA_MODEL_TIMEOUT_MS must be between 1000 and 120000");
        }
        if (inputCostPerMillionTokens != null && inputCostPerMillionTokens < 0) {
            throw new IllegalStateException("ATHENA_MODEL_INPUT_COST_PER_MILLION must not be negative");
        }
        if (outputCostPerMillionTokens != null && outputCostPerMillionTokens < 0) {
            throw new IllegalStateException("ATHENA_MODEL_OUTPUT_COST_PER_MILLION must not be negative");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
