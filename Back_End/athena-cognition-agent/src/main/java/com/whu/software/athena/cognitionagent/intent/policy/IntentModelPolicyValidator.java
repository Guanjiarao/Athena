package com.whu.software.athena.cognitionagent.intent.policy;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelSuggestion;

import java.util.Locale;

/** Blocks model rationales that make claims outside the first node's authority. */
public class IntentModelPolicyValidator {

    public PolicyValidationResult validate(IntentModelSuggestion suggestion) {
        if (suggestion == null || suggestion.suggestedIntent() == null
                || suggestion.rationale() == null || suggestion.rationale().isBlank()) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED, "modelSuggestion", "model suggestion is incomplete");
        }

        String rationale = suggestion.rationale().toLowerCase(Locale.ROOT);
        if (containsForbiddenClaim(rationale)) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    "modelSuggestion.rationale",
                    "model rationale contains a diagnosis, probability, symptom assertion, or write instruction");
        }
        return PolicyValidationResult.pass();
    }

    private boolean containsForbiddenClaim(String rationale) {
        String[] forbiddenTerms = {
                "diagnos", "probability", "symptomconfirmed", "topiccreated", "databaseaction",
                "患有", "确诊", "诊断", "疾病", "概率", "百分之", "%", "你一定", "你是"
        };
        for (String term : forbiddenTerms) {
            if (rationale.contains(term)) {
                return true;
            }
        }
        return rationale.contains("you have") || rationale.contains("you suffer")
                || rationale.contains("must have");
    }
}
