

package com.nageoffer.ai.ragent.triage.normalization;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.List;

final class CompatibilityFactScope {

    boolean shouldEmitCompatibilityFact(TriageContext context, SlotCode slotCode) {
        if (context == null || slotCode == null) {
            return false;
        }
        List<SlotCode> lastAskedSlots = context.getLastAskedSlots() == null ? List.of() : context.getLastAskedSlots();
        if (lastAskedSlots.contains(slotCode)) {
            return true;
        }
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        return pendingSlots.contains(slotCode);
    }

    boolean isAnsweredByTurnUnderstanding(TriageContext context, SlotCode slotCode) {
        if (context == null || slotCode == null) {
            return false;
        }
        TurnUnderstanding understanding = context.getLatestTurnUnderstanding();
        if (understanding == null || understanding.getAnsweredSlots() == null || understanding.getAnsweredSlots().isEmpty()) {
            return false;
        }
        return understanding.getAnsweredSlots().stream().anyMatch(answered -> answered != null && answered.getSlot() == slotCode);
    }

    boolean hasPrimaryComplaintUnderstanding(TriageContext context) {
        if (context == null) {
            return false;
        }
        TurnUnderstanding understanding = context.getLatestTurnUnderstanding();
        if (understanding == null) {
            return false;
        }
        ComplaintUnderstanding complaint = understanding.getPrimaryComplaint();
        return complaint != null && StrUtil.isNotBlank(complaint.getValue());
    }
}
