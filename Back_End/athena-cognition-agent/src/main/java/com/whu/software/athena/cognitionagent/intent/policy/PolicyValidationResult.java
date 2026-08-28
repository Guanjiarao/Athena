package com.whu.software.athena.cognitionagent.intent.policy;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;

public record PolicyValidationResult(boolean allowed,
                                     AgentErrorCode errorCode,
                                     String field,
                                     String message) {

    public static PolicyValidationResult pass() {
        return new PolicyValidationResult(true, null, null, null);
    }

    public static PolicyValidationResult block(AgentErrorCode code,
                                               String field,
                                               String message) {
        return new PolicyValidationResult(false, code, field, message);
    }
}
