

package com.nageoffer.ai.ragent.triage.engine;

/**
 * Events that drive the triage FSM.
 */
public enum TriageEvent {

    START_ANALYSIS,

    PARSE_SUCCESS,

    MISSING_INFO,

    INFO_COMPLETE,

    HIGH_RISK,

    LOW_RISK,

    REPORT_READY,

    FAILURE
}
