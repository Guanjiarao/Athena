package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;
import com.whu.software.athena.cognitionagent.intent.contract.AgentContract;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;

/**
 * Deterministic stand-in for a model provider.
 *
 * The current node already receives the user's explicit intent, so this
 * provider is deliberately not used to make the production decision. It is
 * only a replaceable adapter and a stable test double for later model wiring.
 */
public class MockIntentModelProvider implements IntentModelProvider {

    private final ClueIntent forcedIntent;

    public MockIntentModelProvider() {
        this(null);
    }

    /** Allows tests to simulate a model suggestion that conflicts with the user choice. */
    public MockIntentModelProvider(ClueIntent forcedIntent) {
        this.forcedIntent = forcedIntent;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-intent-v1";
    }

    @Override
    public IntentModelSuggestion suggest(IntentModelContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        ClueIntent suggestion = forcedIntent != null ? forcedIntent : context.explicitIntent();
        String rationale = forcedIntent == null
                ? "mock provider echoes the explicit input for adapter testing"
                : "mock provider uses the test-forced suggestion";
        return new IntentModelSuggestion(providerName(), modelName(),
                AgentContract.PROMPT_VERSION, suggestion, rationale);
    }
}
