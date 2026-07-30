

package com.nageoffer.ai.ragent.triage.model;

public enum AskabilityReason {
    ASKABLE,
    ANSWERED_ALREADY,
    NEGATED_ALREADY,
    CORRECTED_ALREADY,
    ALREADY_PLANNED,
    BLOCKED_BY_POLICY
}
