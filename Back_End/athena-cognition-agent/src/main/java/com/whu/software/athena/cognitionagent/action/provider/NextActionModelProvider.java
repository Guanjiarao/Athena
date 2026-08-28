package com.whu.software.athena.cognitionagent.action.provider;

import com.whu.software.athena.cognitionagent.action.context.NextActionModelContext;

public interface NextActionModelProvider {

    String providerName();

    String modelName();

    NextActionModelSuggestion plan(NextActionModelContext context);
}
