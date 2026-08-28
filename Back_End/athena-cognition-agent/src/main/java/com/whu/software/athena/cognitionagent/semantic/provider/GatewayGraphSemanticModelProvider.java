package com.whu.software.athena.cognitionagent.semantic.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.contract.GraphContract;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.schema.SchemaValidationResult;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import com.whu.software.athena.cognitionagent.model.ModelRequest;
import com.whu.software.athena.cognitionagent.model.ModelResponse;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticModelContext;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.schema.SemanticModelOutputSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayGraphSemanticModelProvider implements GraphSemanticModelProvider {

    private static final Logger log = LoggerFactory.getLogger(GatewayGraphSemanticModelProvider.class);

    private final ModelGateway gateway;
    private final ObjectMapper mapper;
    private final GraphSemanticPromptBuilder prompts;
    private final SemanticModelOutputSchemaValidator schemaValidator =
            new SemanticModelOutputSchemaValidator();

    public GatewayGraphSemanticModelProvider(ModelGateway gateway, ObjectMapper mapper) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.prompts = new GraphSemanticPromptBuilder(mapper);
    }

    @Override public String providerName() { return gateway.providerName(); }
    @Override public String modelName() { return gateway.modelName(); }

    @Override
    public SemanticModelSuggestion generate(GraphSemanticModelContext context) {
        ModelResponse response = gateway.complete(new ModelRequest(
                GraphContract.SEMANTIC_PROMPT_VERSION,
                prompts.systemPrompt(), prompts.userPrompt(context), 1200));
        SchemaValidationResult validation = schemaValidator.validate(response.output());
        if (!validation.valid()) {
            // integration debugging: log the violations and a truncated raw output so the
            // exact model deviation is diagnosable without persisting full prompts
            String raw;
            try {
                raw = mapper.writeValueAsString(response.output());
            } catch (Exception exception) {
                raw = String.valueOf(response.output());
            }
            log.warn("semantic model schema failed: {}; rawOutput(truncated)={}",
                    String.join("; ", validation.violations()),
                    raw.length() > 600 ? raw.substring(0, 600) : raw);
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "semantic model schema failed: "
                            + String.join("; ", validation.violations()), false);
        }
        try {
            GraphSemanticUpdateDraft draft =
                    mapper.treeToValue(response.output(), GraphSemanticUpdateDraft.class);
            return new SemanticModelSuggestion(
                    response.provider(), response.modelName(),
                    GraphContract.SEMANTIC_PROMPT_VERSION, draft,
                    response.inputTokens(), response.outputTokens(), response.totalTokens(),
                    response.estimatedCost());
        } catch (Exception exception) {
            throw new IntentModelProviderException(AgentErrorCode.MODEL_OUTPUT_INVALID,
                    "semantic model output cannot be parsed", false);
        }
    }
}
