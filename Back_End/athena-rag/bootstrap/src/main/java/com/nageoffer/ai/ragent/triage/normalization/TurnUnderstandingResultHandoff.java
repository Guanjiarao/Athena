

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnIntent;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.function.Supplier;

public class TurnUnderstandingResultHandoff {
    private final TurnUnderstandingPostProcessor turnUnderstandingPostProcessor;

    public TurnUnderstandingResultHandoff(TurnUnderstandingPostProcessor turnUnderstandingPostProcessor) {
        this.turnUnderstandingPostProcessor = turnUnderstandingPostProcessor;
    }

    public TurnUnderstanding handoff(TriageContext context,
                                     String latestTurn,
                                     Supplier<TurnUnderstanding> parsedResultSupplier) {
        TurnUnderstanding fallback = buildFallback();
        TurnUnderstanding understanding;
        try {
            understanding = parsedResultSupplier == null ? fallback : parsedResultSupplier.get();
        } catch (Exception ignored) {
            understanding = fallback;
        }
        return turnUnderstandingPostProcessor.normalizeAndEnrich(context, understanding, fallback, latestTurn);
    }

    private TurnUnderstanding buildFallback() {
        return TurnUnderstanding.builder().intent(TurnIntent.UNKNOWN).confidence(0.0D).build();
    }
}
