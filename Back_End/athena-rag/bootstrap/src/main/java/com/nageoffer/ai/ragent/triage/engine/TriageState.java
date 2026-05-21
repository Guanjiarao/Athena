

package com.nageoffer.ai.ragent.triage.engine;

/**
 * Explicit finite-state-machine states for triage orchestration.
 */
public enum TriageState {

    INIT(false),

    PARSING(false),

    VALIDATING(false),

    RISK_ASSESSING(false),

    REPORT_GENERATING(false),

    COMPLETED(true),

    INTERRUPTED(true);

    private final boolean terminal;

    TriageState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
