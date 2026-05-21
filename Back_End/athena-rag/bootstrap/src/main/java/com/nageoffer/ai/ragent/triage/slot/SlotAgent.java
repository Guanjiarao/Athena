

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Slot Agent。
 *
 * <p>SlotAgent 是 slot 包的对外边界：消费 NormalizedTurn 中的 facts/answeredSlots，产出 slotPatch。
 * 第一阶段仍包裹 StateReducer + SlotManager，并保留旧 worker 直接写 TriageContext 的行为。</p>
 */
@Component
@RequiredArgsConstructor
public class SlotAgent {

    private final StateReducer stateReducer;
    private final SlotManager slotManager;

    public SlotAgentResult reduce(TriageContext context, NormalizedTurn normalizedTurn) {
        if (context == null) {
            return SlotAgentResult.builder().build();
        }
        context.ensureCollections();
        TriageContext workingContext = cloneSlotWorkingContext(context);
        stateReducer.execute(workingContext);
        slotManager.execute(workingContext);
        return SlotAgentResult.builder()
                .slotPatch(workingContext.getSlotState() == null || workingContext.getSlotState().getSlots() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(workingContext.getSlotState().getSlots()))
                .answeredSlots(normalizedTurn == null || normalizedTurn.getAnsweredSlots() == null
                        ? new ArrayList<SlotCode>()
                        : new ArrayList<>(normalizedTurn.getAnsweredSlots()))
                .build();
    }

    private TriageContext cloneSlotWorkingContext(TriageContext source) {
        TriageContext clone = TriageContext.builder()
                .sessionId(source.getSessionId())
                .userInput(source.getUserInput())
                .latestUserTurn(source.getLatestUserTurn())
                .conversationSummary(source.getConversationSummary())
                .finalPrimaryComplaint(source.getFinalPrimaryComplaint())
                .latestTurnUnderstanding(source.getLatestTurnUnderstanding())
                .factHistory(source.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(source.getFactHistory()))
                .extractedSymptoms(source.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(source.getExtractedSymptoms()))
                .answeredSlots(source.getAnsweredSlots() == null ? new ArrayList<>() : new ArrayList<>(source.getAnsweredSlots()))
                .lastAskedSlots(source.getLastAskedSlots() == null ? new ArrayList<>() : new ArrayList<>(source.getLastAskedSlots()))
                .slotState(cloneSlotState(source.getSlotState()))
                .build();
        clone.ensureCollections();
        return clone;
    }

    private SlotState cloneSlotState(SlotState source) {
        SlotState clone = SlotState.empty();
        if (source != null && source.getSlots() != null) {
            clone.setSlots(new LinkedHashMap<>(source.getSlots()));
        }
        return clone;
    }
}
