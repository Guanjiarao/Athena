package com.whu.software.athena.cognitionagent.target.provider;

import com.whu.software.athena.cognitionagent.target.context.GraphTargetModelContext;

public interface GraphTargetModelProvider {

    String providerName();

    String modelName();

    TargetModelSuggestion resolve(GraphTargetModelContext context);
}
