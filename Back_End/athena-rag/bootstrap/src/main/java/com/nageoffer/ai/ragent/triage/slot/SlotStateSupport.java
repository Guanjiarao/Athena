

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SlotStateSupport {

    void mergeFact(SlotState slotState, Fact fact) {
        SlotStatus slotStatus = resolveSlotStatus(fact);
        SlotValue existing = slotState.get(fact.getSlot());
        if (existing != null
                && isResolved(existing.getStatus())
                && existing.getValue() != null
                && !existing.getValue().equals(fact.getCanonicalValue())) {
            slotState.put(SlotValue.builder()
                    .slot(fact.getSlot())
                    .value(fact.getCanonicalValue())
                    .status(SlotStatus.CONFLICTING)
                    .evidence(fact.getEvidence())
                    .updatedAt(Instant.now())
                    .build());
            return;
        }
        slotState.put(SlotValue.builder()
                .slot(fact.getSlot())
                .value(fact.getCanonicalValue())
                .status(slotStatus)
                .evidence(fact.getEvidence())
                .updatedAt(Instant.now())
                .build());
    }

    void applyProjectedSlots(SlotState slotState, Map<SlotCode, SlotValue> projectedSlots) {
        if (slotState == null || projectedSlots == null || projectedSlots.isEmpty()) {
            return;
        }
        slotState.ensureInitialized();
        slotState.getSlots().putAll(projectedSlots);
    }

    void ensureUnknownPrimarySymptomIfMissing(SlotState slotState) {
        SlotValue primary = slotState.get(SlotCode.PRIMARY_SYMPTOM);
        if (primary == null) {
            slotState.put(SlotValue.builder()
                    .slot(SlotCode.PRIMARY_SYMPTOM)
                    .value(null)
                    .status(SlotStatus.UNKNOWN)
                    .evidence("主诉尚未明确")
                    .updatedAt(Instant.now())
                    .build());
        }
    }

    List<Symptom> buildCompatibilitySymptoms(SlotState slotState) {
        List<Symptom> symptoms = new ArrayList<>();
        SlotValue primarySymptom = slotState.get(SlotCode.PRIMARY_SYMPTOM);
        if (primarySymptom == null || !isResolved(primarySymptom.getStatus())) {
            return symptoms;
        }
        SlotValue bodyPart = slotState.get(SlotCode.BODY_PART);
        SlotValue duration = slotState.get(SlotCode.DURATION);
        SlotValue severity = slotState.get(SlotCode.PAIN_SEVERITY);

        List<String> accompanyingSymptoms = new ArrayList<>();
        appendPresenceSymptom(accompanyingSymptoms, slotState, SlotCode.FEVER_PRESENCE, "发热");
        appendPresenceSymptom(accompanyingSymptoms, slotState, SlotCode.NAUSEA_PRESENCE, "恶心");
        appendPresenceSymptom(accompanyingSymptoms, slotState, SlotCode.VOMITING_PRESENCE, "呕吐");
        appendPresenceSymptom(accompanyingSymptoms, slotState, SlotCode.DYSPNEA_PRESENCE, "呼吸困难");
        appendPresenceSymptom(accompanyingSymptoms, slotState, SlotCode.DIARRHEA_PRESENCE, "腹泻");

        symptoms.add(Symptom.builder()
                .name(primarySymptom.getValue())
                .bodyPart(bodyPart == null ? null : bodyPart.getValue())
                .duration(duration == null ? null : duration.getValue())
                .severity(severity == null ? null : severity.getValue())
                .accompanyingSymptoms(accompanyingSymptoms)
                .build());
        return symptoms;
    }

    private SlotStatus resolveSlotStatus(Fact fact) {
        if (fact == null) {
            return SlotStatus.UNKNOWN;
        }
        if (fact.getPolarity() == FactPolarity.NEGATIVE) {
            return SlotStatus.NEGATED;
        }
        if (fact.getPolarity() == FactPolarity.UNCERTAIN) {
            return SlotStatus.UNKNOWN;
        }
        return SlotStatus.FILLED;
    }

    private void appendPresenceSymptom(List<String> symptoms, SlotState slotState, SlotCode slotCode, String symptomName) {
        SlotValue slotValue = slotState.get(slotCode);
        if (slotValue == null || !isResolved(slotValue.getStatus())) {
            return;
        }
        if ("YES".equalsIgnoreCase(slotValue.getValue())) {
            symptoms.add(symptomName);
        }
    }

    private boolean isResolved(SlotStatus slotStatus) {
        return slotStatus == SlotStatus.FILLED
                || slotStatus == SlotStatus.NEGATED
                || slotStatus == SlotStatus.CORRECTED
                || slotStatus == SlotStatus.INFERRED;
    }
}
