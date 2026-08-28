package com.whu.software.athena.cognitionagent.action.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.action.context.NextActionModelContext;
import com.whu.software.athena.cognitionagent.action.schema.NextActionModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import com.whu.software.athena.cognitionagent.model.ModelRequest;
import com.whu.software.athena.cognitionagent.model.ModelResponse;

public class GatewayNextActionModelProvider implements NextActionModelProvider {

    private final ModelGateway gateway;
    private final ObjectMapper mapper;
    private final NextActionPromptBuilder prompts;
    private final NextActionModelOutputSchemaValidator schemaValidator =
            new NextActionModelOutputSchemaValidator();

    public GatewayNextActionModelProvider(ModelGateway gateway, ObjectMapper mapper) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.prompts = new NextActionPromptBuilder(mapper);
    }

    @Override public String providerName() { return gateway.providerName(); }
    @Override public String modelName() { return gateway.modelName(); }

    @Override
    public NextActionModelSuggestion plan(NextActionModelContext context) {
        ModelResponse response = gateway.complete(new ModelRequest(
                GraphContract.ACTION_PROMPT_VERSION,
                prompts.systemPrompt(), prompts.userPrompt(context), 700));
        SchemaValidationResult validation = schemaValidator.validate(response.output());
        if (!validation.valid()) {
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "next action model schema failed: "
                            + String.join("; ", validation.violations()), false);
        }
        try {
            NextActionModelOutput output =
                    mapper.treeToValue(response.output(), NextActionModelOutput.class);
            return new NextActionModelSuggestion(
                    response.provider(), response.modelName(),
                    GraphContract.ACTION_PROMPT_VERSION, output,
                    response.inputTokens(), response.outputTokens(), response.totalTokens(),
                    response.estimatedCost());
        } catch (Exception exception) {
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "next action model output cannot be parsed", false);
        }
    }
}
