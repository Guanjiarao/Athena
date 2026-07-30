

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.CorrectionTarget;
import com.nageoffer.ai.ragent.triage.model.CorrectionUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ReducerCorrectionApplier {

    public void apply(Map<SlotCode, SlotValue> reducedSlots,
                      LinkedHashSet<SlotCode> answeredSlots,
                      LinkedHashSet<SlotCode> pendingCandidates,
                      List<CorrectionUnderstanding> correctionLog,
                      List<CorrectionUnderstanding> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return;
        }
        for (CorrectionUnderstanding correction : corrections) {
            if (correction == null) {
                continue;
            }
            correctionLog.add(correction);
            SlotCode correctedSlot = resolveCorrectedSlot(reducedSlots, correction);
            if (correctedSlot == null) {
                continue;
            }
            String confirmValue = trim(correction.getConfirmValue());
            if (blank(confirmValue)) {
                continue;
            }
            reducedSlots.put(correctedSlot, SlotValue.builder()
                    .slot(correctedSlot)
                    .value(confirmValue)
                    .status(SlotStatus.CORRECTED)
                    .evidence(correction.getEvidence())
                    .updatedAt(Instant.now())
                    .build());
            answeredSlots.add(correctedSlot);
            pendingCandidates.remove(correctedSlot);
        }
    }

    private SlotCode resolveCorrectedSlot(Map<SlotCode, SlotValue> reducedSlots, CorrectionUnderstanding correction) {
        if (correction == null) {
            return null;
        }
        if (correction.getTarget() == CorrectionTarget.PRIMARY_COMPLAINT) {
            return SlotCode.PRIMARY_SYMPTOM;
        }
        if (correction.getSlot() != null) {
            return correction.getSlot();
        }
        if (correction.getTarget() != CorrectionTarget.SLOT_VALUE) {
            return null;
        }
        if (blank(correction.getRejectValue())) {
            return null;
        }
        SlotCode matchedSlot = null;
        for (Map.Entry<SlotCode, SlotValue> entry : reducedSlots.entrySet()) {
            SlotValue slotValue = entry.getValue();
            if (slotValue == null || blank(slotValue.getValue())) {
                continue;
            }
            if (!slotValue.getValue().equals(correction.getRejectValue().trim())) {
                continue;
            }
            if (matchedSlot != null) {
                return null;
            }
            matchedSlot = entry.getKey();
        }
        return matchedSlot;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }
}
