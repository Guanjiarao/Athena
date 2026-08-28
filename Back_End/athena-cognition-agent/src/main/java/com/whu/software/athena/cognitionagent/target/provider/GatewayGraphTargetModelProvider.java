package com.whu.software.athena.cognitionagent.target.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import com.whu.software.athena.cognitionagent.model.ModelRequest;
import com.whu.software.athena.cognitionagent.model.ModelResponse;
import com.whu.software.athena.cognitionagent.target.context.GraphTargetModelContext;
import com.whu.software.athena.cognitionagent.target.schema.TargetModelOutputSchemaValidator;

public class GatewayGraphTargetModelProvider implements GraphTargetModelProvider {

    private final ModelGateway gateway;
    private final GraphTargetPromptBuilder prompts;
    private final TargetModelOutputSchemaValidator schemaValidator =
            new TargetModelOutputSchemaValidator();

    public GatewayGraphTargetModelProvider(ModelGateway gateway, ObjectMapper mapper) {
        this.gateway = gateway;
        this.prompts = new GraphTargetPromptBuilder(mapper);
    }

    @Override public String providerName() { return gateway.providerName(); }
    @Override public String modelName() { return gateway.modelName(); }

    @Override
    public TargetModelSuggestion resolve(GraphTargetModelContext context) {
        ModelResponse response = gateway.complete(new ModelRequest(
                GraphContract.TARGET_PROMPT_VERSION,
                prompts.systemPrompt(), prompts.userPrompt(context), 350));
        SchemaValidationResult validation = schemaValidator.validate(response.output());
        if (!validation.valid()) {
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "target model schema failed: " + String.join("; ", validation.violations()),
                    false);
        }
        JsonNode output = response.output();
        return new TargetModelSuggestion(response.provider(), response.modelName(),
                GraphContract.TARGET_PROMPT_VERSION,
                GraphUpdateRoute.valueOf(output.path("route").asText()),
                output.path("matchedTopicId").isNull()
                        ? null : output.path("matchedTopicId").asText(),
                output.path("suggestedTopicTitle").isNull()
                        ? null : output.path("suggestedTopicTitle").asText(),
                output.path("rationale").asText(),
                response.inputTokens(), response.outputTokens(), response.totalTokens(),
                response.estimatedCost());
    }
}
