package com.whu.software.athena.cognitionagent.intent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.schema.IntentModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import com.whu.software.athena.cognitionagent.model.ModelRequest;
import com.whu.software.athena.cognitionagent.model.ModelResponse;

/** Intent-node adapter over the single shared model gateway. */
public class GatewayIntentModelProvider implements IntentModelProvider {

    private final ModelGateway gateway;
    private final IntentModelPromptBuilder promptBuilder;
    private final IntentModelOutputSchemaValidator schemaValidator =
            new IntentModelOutputSchemaValidator();

    public GatewayIntentModelProvider(ModelGateway gateway, ObjectMapper mapper) {
        this.gateway = gateway;
        this.promptBuilder = new IntentModelPromptBuilder(mapper);
    }

    @Override
    public String providerName() {
        return gateway.providerName();
    }

    @Override
    public String modelName() {
        return gateway.modelName();
    }

    @Override
    public IntentModelSuggestion suggest(IntentModelContext context) {
        ModelResponse response = gateway.complete(new ModelRequest(
                AgentContract.PROMPT_VERSION,
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(context),
                300));
        JsonNode output = response.output();
        SchemaValidationResult validation = schemaValidator.validate(output);
        if (!validation.valid()) {
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "model output schema validation failed: "
                            + String.join("; ", validation.violations()), false);
        }
        return new IntentModelSuggestion(response.provider(), response.modelName(),
                AgentContract.PROMPT_VERSION,
                ClueIntent.valueOf(output.path("suggestedIntent").asText()),
                output.path("rationale").asText(),
                response.inputTokens(), response.outputTokens(), response.totalTokens(),
                response.estimatedCost());
    }
}
