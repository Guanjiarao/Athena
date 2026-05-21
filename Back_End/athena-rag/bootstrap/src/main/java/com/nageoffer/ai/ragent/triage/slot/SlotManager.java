

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.StateReducerResult;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.HeuristicGovernanceTags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class SlotManager {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    private final SlotStateSupport slotStateSupport;
    private final CompatibilitySlotFallback compatibilitySlotFallback;

    @Autowired
    public SlotManager(SlotStateSupport slotStateSupport) {
        this(slotStateSupport, new CompatibilitySlotFallback());
    }

    SlotManager(SlotStateSupport slotStateSupport, CompatibilitySlotFallback compatibilitySlotFallback) {
        this.slotStateSupport = slotStateSupport;
        this.compatibilitySlotFallback = compatibilitySlotFallback;
    }

    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();
        SlotState slotState = context.getSlotState();
        slotState.ensureInitialized();

        StateReducerResult reducerResult = context.getLatestStateReducerResult();
        if (hasReducerProjection(reducerResult)) {
            applyReducerProjection(context, slotState, reducerResult);
        } else {
            applyCompatibilityFallback(context);
        }

        if (!hasFilledPrimarySymptom(slotState)) {
            slotStateSupport.ensureUnknownPrimarySymptomIfMissing(slotState);
        }

        context.setSlotState(slotState);
        context.setExtractedSymptoms(slotStateSupport.buildCompatibilitySymptoms(slotState));
        return context;
    }

    private boolean hasReducerProjection(StateReducerResult reducerResult) {
        return reducerResult != null && reducerResult.getReducedSlots() != null && !reducerResult.getReducedSlots().isEmpty();
    }

    private void applyReducerProjection(TriageContext context, SlotState slotState, StateReducerResult reducerResult) {
        slotStateSupport.applyProjectedSlots(slotState, reducerResult.getReducedSlots());
        syncComplaintProjection(slotState, reducerResult.getComplaintTruth());
        context.setAnsweredSlots(reducerResult.getAnsweredSlots() == null
                ? new ArrayList<>()
                : new ArrayList<>(reducerResult.getAnsweredSlots()));
        context.setPendingSlots(reducerResult.getPendingCandidates() == null
                ? new ArrayList<>()
                : new ArrayList<>(reducerResult.getPendingCandidates()));
    }

    private void syncComplaintProjection(SlotState slotState, ComplaintUnderstanding complaintTruth) {
        if (slotState == null) {
            return;
        }
        if (complaintTruth == null || complaintTruth.getValue() == null || complaintTruth.getValue().isBlank()) {
            return;
        }
        slotState.put(SlotValue.builder()
                .slot(SlotCode.PRIMARY_SYMPTOM)
                .value(complaintTruth.getValue().trim())
                .status(com.nageoffer.ai.ragent.triage.model.SlotStatus.FILLED)
                .evidence(complaintTruth.getEvidence())
                .updatedAt(Instant.now())
                .build());
    }

    private void applyCompatibilityFallback(TriageContext context) {
        compatibilitySlotFallback.mergeFactsIntoSlotState(context, slotStateSupport);
        context.setAnsweredSlots(compatibilitySlotFallback.resolveCompatibilityAnsweredSlots(context));
    }

    private boolean hasFilledPrimarySymptom(SlotState slotState) {
        SlotValue value = slotState.get(SlotCode.PRIMARY_SYMPTOM);
        return value != null && value.getValue() != null && value.getStatus() != null
                && (value.getStatus().name().equals("FILLED")
                || value.getStatus().name().equals("CORRECTED")
                || value.getStatus().name().equals("INFERRED"));
    }
}
