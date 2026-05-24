

package com.nageoffer.ai.ragent.triage.slot;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Slot Agent 是多 Agent 架构中的槽位状态更新器。
 *
 * <p>对外保持 result-only：消费 NormalizedTurn 中的 facts/answeredSlots，产出 SlotAgentResult，
 * 不直接写原始 TriageContext。当前内部复用 StateReducer + SlotManager，并限制其只作用在 workingContext 上；
 * 状态写回统一由 TriageContextReducer 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class SlotAgent {

    private final StateReducer stateReducer;
    private final SlotManager slotManager;

    @RagTraceNode(name = "SlotAgent", type = "TRIAGE_SLOT")
    public SlotAgentResult reduce(TriageContext context, NormalizedTurn normalizedTurn) {
        if (context == null) {
            return SlotAgentResult.builder().build();
        }
        context.ensureCollections();
        TriageContext workingContext = cloneSlotWorkingContext(context);
        stateReducer.execute(workingContext);
        slotManager.execute(workingContext);
        applyAnsweredSlotsFromTurnUnderstanding(workingContext);
        return SlotAgentResult.builder()
                .slotPatch(workingContext.getSlotState() == null || workingContext.getSlotState().getSlots() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(workingContext.getSlotState().getSlots()))
                .answeredSlots(normalizedTurn == null || normalizedTurn.getAnsweredSlots() == null
                        ? new ArrayList<SlotCode>()
                        : new ArrayList<>(normalizedTurn.getAnsweredSlots()))
                .build();
    }

    private void applyAnsweredSlotsFromTurnUnderstanding(TriageContext context) {
        if (context == null || context.getLatestTurnUnderstanding() == null
                || context.getLatestTurnUnderstanding().getAnsweredSlots() == null
                || context.getLatestTurnUnderstanding().getAnsweredSlots().isEmpty()) {
            return;
        }
        SlotState slotState = context.getSlotState() == null ? SlotState.empty() : context.getSlotState();
        slotState.ensureInitialized();
        for (AnsweredSlotUnderstanding answered : context.getLatestTurnUnderstanding().getAnsweredSlots()) {
            if (answered == null || answered.getSlot() == null || StrUtil.isBlank(answered.getNormalizedValue())) {
                continue;
            }
            SlotStatus status = answered.getAssertion() == AssertionStatus.ABSENT ? SlotStatus.NEGATED : SlotStatus.FILLED;
            slotState.put(SlotValue.builder()
                    .slot(answered.getSlot())
                    .value(answered.getNormalizedValue().trim())
                    .status(status)
                    .evidence(StrUtil.blankToDefault(answered.getEvidence(), "turn_understanding.answeredSlots"))
                    .updatedAt(Instant.now())
                    .build());
        }
        context.setSlotState(slotState);
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
