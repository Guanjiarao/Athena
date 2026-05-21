

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.HeuristicGovernanceTags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class CompatibilitySlotFallback {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    void mergeFactsIntoSlotState(TriageContext context, SlotStateSupport slotStateSupport) {
        if (context == null || slotStateSupport == null || context.getFactHistory() == null) {
            return;
        }
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null || fact.getCanonicalValue() == null) {
                continue;
            }
            slotStateSupport.mergeFact(context.getSlotState(), fact);
        }
    }

    List<SlotCode> resolveCompatibilityAnsweredSlots(TriageContext context) {
        if (context == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<SlotCode> answered = new LinkedHashSet<>();
        List<SlotCode> lastAsked = context.getLastAskedSlots() == null ? List.of() : context.getLastAskedSlots();
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        if (lastAsked.isEmpty() || pendingSlots.isEmpty() || context.getFactHistory() == null) {
            return new ArrayList<>();
        }
        int latestTurnIndex = Math.max(0, context.getConversationHistory().size() - 1);
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null) {
                continue;
            }
            if (fact.getSourceTurnIndex() != latestTurnIndex) {
                continue;
            }
            if (lastAsked.contains(fact.getSlot()) && pendingSlots.contains(fact.getSlot())) {
                answered.add(fact.getSlot());
            }
        }
        return new ArrayList<>(answered);
    }
}
