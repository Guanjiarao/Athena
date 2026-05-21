

package com.nageoffer.ai.ragent.triage.model;

/**
 * Slot lifecycle status in the current triage session.
 */
public enum SlotStatus {

    FILLED,

    NEGATED,

    CORRECTED,

    INFERRED,

    MISSING,

    UNKNOWN,

    CONFLICTING
}
