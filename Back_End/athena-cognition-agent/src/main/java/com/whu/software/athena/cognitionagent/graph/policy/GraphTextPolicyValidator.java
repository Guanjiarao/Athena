package com.whu.software.athena.cognitionagent.graph.policy;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.policy.PolicyValidationResult;

import java.util.List;
import java.util.Locale;

/** First safety vocabulary for model-written health text. */
public class GraphTextPolicyValidator {

    private static final List<String> FORBIDDEN = List.of(
            "你患有", "你一定", "确诊", "诊断为", "患病概率", "风险概率",
            "insert ", "update ", "delete ", "databaseaction", "topiccreated",
            "you have ", "diagnosed with", "probability", "percent chance");

    public PolicyValidationResult validate(String field, String text) {
        if (text == null) return PolicyValidationResult.pass();
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String token : FORBIDDEN) {
            if (normalized.contains(token)) {
                return PolicyValidationResult.block(AgentErrorCode.POLICY_BLOCKED,
                        field, "model text crosses the non-diagnostic or no-write boundary");
            }
        }
        return PolicyValidationResult.pass();
    }
}
