package com.whu.software.athena.cognitionagent.intent.contract;

/** Model suggestion exposed in the internal node result without business authority. */
public class IntentModelSuggestionView {

    public String provider;
    public String modelName;
    public String promptVersion;
    public ClueIntent suggestedIntent;
    public String rationale;
    public Integer inputTokens;
    public Integer outputTokens;
    public Integer totalTokens;
    public Double estimatedCost;
}
