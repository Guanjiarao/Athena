

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

final class SemanticSignalResolver {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    boolean hasPrimarySignalFact(TriageContext context, String semanticSignal) {
        if (context == null || semanticSignal == null || semanticSignal.isBlank() || context.getFactHistory() == null) {
            return false;
        }
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null || fact.getCanonicalValue() == null) {
                continue;
            }
            if (fact.getSlot() == SlotCode.PRIMARY_SYMPTOM && semanticSignal.equals(fact.getCanonicalValue())) {
                return true;
            }
        }
        return false;
    }
}
