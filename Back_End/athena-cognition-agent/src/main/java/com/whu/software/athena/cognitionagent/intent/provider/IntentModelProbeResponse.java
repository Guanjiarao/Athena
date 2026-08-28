package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.contract.AgentError;

/** Response for the local-only model probe; it is not a business result. */
public class IntentModelProbeResponse {

    public String status;
    public IntentModelSuggestion suggestion;
    public AgentError error;

    public static IntentModelProbeResponse success(IntentModelSuggestion suggestion) {
        IntentModelProbeResponse response = new IntentModelProbeResponse();
        response.status = "PROVIDER_SUCCEEDED";
        response.suggestion = suggestion;
        return response;
    }

    public static IntentModelProbeResponse failure(AgentError error) {
        IntentModelProbeResponse response = new IntentModelProbeResponse();
        response.status = "PROVIDER_FAILED";
        response.error = error;
        return response;
    }
}
