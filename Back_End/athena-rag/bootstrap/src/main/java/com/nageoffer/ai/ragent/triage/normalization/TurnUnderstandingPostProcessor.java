

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnIntent;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

public class TurnUnderstandingPostProcessor {
    private final TurnComplaintSemanticsCoordinator turnComplaintSemanticsCoordinator;

    public TurnUnderstandingPostProcessor(TurnComplaintSemanticsCoordinator turnComplaintSemanticsCoordinator) {
        this.turnComplaintSemanticsCoordinator = turnComplaintSemanticsCoordinator;
    }

    public TurnUnderstanding normalizeAndEnrich(TriageContext context,
                                                TurnUnderstanding understanding,
                                                TurnUnderstanding fallback,
                                                String latestTurn) {
        TurnUnderstanding normalized = understanding == null ? fallback : understanding;
        if (normalized == null) {
            normalized = TurnUnderstanding.builder().intent(TurnIntent.UNKNOWN).confidence(0.0D).build();
        }
        if (normalized.getIntent() == null) {
            normalized.setIntent(TurnIntent.UNKNOWN);
        }
        turnComplaintSemanticsCoordinator.enrich(context, normalized, latestTurn);
        return normalized;
    }
}
