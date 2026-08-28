package com.whu.software.athena.cognitionagent.semantic.provider;

import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticModelContext;

public interface GraphSemanticModelProvider {

    String providerName();

    String modelName();

    SemanticModelSuggestion generate(GraphSemanticModelContext context);
}
