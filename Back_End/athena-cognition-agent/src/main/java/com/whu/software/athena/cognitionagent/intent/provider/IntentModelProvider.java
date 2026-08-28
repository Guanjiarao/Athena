package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.context.IntentModelContext;

/**
 * Adapter boundary for a future model provider.
 *
 * The provider returns a suggestion only. It cannot persist business state or
 * replace the deterministic rules and Policy validation of the node.
 */
public interface IntentModelProvider {

    String providerName();

    String modelName();

    IntentModelSuggestion suggest(IntentModelContext context);
}
