

package com.nageoffer.ai.ragent.triage.risk;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
final class RiskTextSnapshotBuilder {

    String build(TriageContext context) {
        StringBuilder builder = new StringBuilder(StrUtil.blankToDefault(context.getUserInput(), ""));
        appendSlotEvidence(builder, context.getSlotState(), SlotCode.PRIMARY_SYMPTOM, null);
        appendSlotEvidence(builder, context.getSlotState(), SlotCode.DURATION, null);
        appendSlotEvidence(builder, context.getSlotState(), SlotCode.BODY_PART, null);
        appendPresence(builder, context.getSlotState(), SlotCode.FEVER_PRESENCE, "发热");
        appendPresence(builder, context.getSlotState(), SlotCode.NAUSEA_PRESENCE, "恶心");
        appendPresence(builder, context.getSlotState(), SlotCode.VOMITING_PRESENCE, "呕吐");
        appendPresence(builder, context.getSlotState(), SlotCode.DYSPNEA_PRESENCE, "呼吸困难");
        appendPresence(builder, context.getSlotState(), SlotCode.BLEEDING_PRESENCE, "出血");
        appendPresence(builder, context.getSlotState(), SlotCode.PREGNANCY_STATUS, "怀孕");
        appendPresence(builder, context.getSlotState(), SlotCode.SEIZURE_PRESENCE, "抽搐");
        appendSlotEvidence(builder, context.getSlotState(), SlotCode.TEMPERATURE, null);

        if (context.getExtractedSymptoms() != null) {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (symptom == null) {
                    continue;
                }
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getName(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getBodyPart(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getDuration(), ""));
                builder.append(" ").append(StrUtil.blankToDefault(symptom.getSeverity(), ""));
                if (symptom.getCharacteristics() != null) {
                    builder.append(" ").append(String.join(" ", symptom.getCharacteristics()));
                }
                if (symptom.getAccompanyingSymptoms() != null) {
                    builder.append(" ").append(String.join(" ", symptom.getAccompanyingSymptoms()));
                }
            }
        }
        return builder.toString();
    }

    private void appendSlotEvidence(StringBuilder builder, SlotState slotState, SlotCode slotCode, List<String> aliases) {
        if (slotState == null || slotCode == null) {
            return;
        }
        SlotValue slotValue = slotState.get(slotCode);
        if (slotValue == null || !isResolved(slotValue.getStatus()) || StrUtil.isBlank(slotValue.getValue())) {
            return;
        }
        if (aliases != null && !aliases.isEmpty()) {
            builder.append(" ").append(String.join(" ", aliases));
        }
        builder.append(" ").append(slotValue.getValue());
    }

    private void appendPresence(StringBuilder builder, SlotState slotState, SlotCode slotCode, String label) {
        if (slotState == null || slotCode == null || StrUtil.isBlank(label)) {
            return;
        }
        SlotValue slotValue = slotState.get(slotCode);
        if (slotValue == null || !isResolved(slotValue.getStatus()) || StrUtil.isBlank(slotValue.getValue())) {
            return;
        }
        if ("YES".equalsIgnoreCase(slotValue.getValue())) {
            builder.append(" ").append(label);
        } else if ("NO".equalsIgnoreCase(slotValue.getValue())) {
            builder.append(" 没有").append(label);
        }
    }

    private boolean isResolved(SlotStatus slotStatus) {
        return slotStatus == SlotStatus.FILLED
                || slotStatus == SlotStatus.NEGATED
                || slotStatus == SlotStatus.CORRECTED
                || slotStatus == SlotStatus.INFERRED;
    }
}
