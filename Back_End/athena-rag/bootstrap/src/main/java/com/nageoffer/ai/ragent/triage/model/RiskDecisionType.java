

package com.nageoffer.ai.ragent.triage.model;

public enum RiskDecisionType {
    NO_RISK_SIGNAL,
    MONITOR,
    ASK_RISK_CLARIFICATION,
    TRIGGER_WARNING,
    ESCALATE_FROM_HISTORY
}
