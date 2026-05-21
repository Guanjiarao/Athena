

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.function.Supplier;

public class TurnUnderstandingExecutionShell {
    private final TurnUnderstandingResultHandoff turnUnderstandingResultHandoff;

    public TurnUnderstandingExecutionShell(TurnUnderstandingResultHandoff turnUnderstandingResultHandoff) {
        this.turnUnderstandingResultHandoff = turnUnderstandingResultHandoff;
    }

    public TriageContext execute(TriageContext context,
                                 String latestTurn,
                                 Supplier<TurnUnderstanding> parsedResultSupplier) {
        if (context == null) context = new TriageContext();
        context.ensureCollections();
        if (latestTurn == null || latestTurn.isBlank()) return context;
        TurnUnderstanding understanding = turnUnderstandingResultHandoff.handoff(context, latestTurn, parsedResultSupplier);
        context.appendTurnUnderstanding(understanding);
        return context;
    }
}
