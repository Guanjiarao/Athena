package com.whu.software.athena.cognitionagent.intent.validation;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;

public record ValidationIssue(AgentErrorCode code, String field, String message) {
}
