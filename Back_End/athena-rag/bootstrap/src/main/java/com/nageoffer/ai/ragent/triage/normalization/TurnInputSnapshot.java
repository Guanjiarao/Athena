

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前轮只读输入快照。
 *
 * <p>Normalization 读取原始输入和会话上下文，Slot 读取 slotStateSnapshot/lastAskedSlots 作为归约基线。
 * 当前阶段旧 worker 仍直接读写 TriageContext，本快照先固化后续拆分边界。</p>
 */
@Data
@Builder
public class TurnInputSnapshot {

    private String sessionId;

    private String latestUserInput;

    private String conversationTranscript;

    private String conversationSummary;

    private Integer totalTurnCount;

    private SlotState slotStateSnapshot;

    @Builder.Default
    private List<SlotCode> lastAskedSlots = new ArrayList<>();

    public static TurnInputSnapshot from(TriageContext context) {
        if (context == null) {
            return TurnInputSnapshot.builder().build();
        }
        context.ensureCollections();
        return TurnInputSnapshot.builder()
                .sessionId(context.getSessionId())
                .latestUserInput(resolveLatestUserInput(context))
                .conversationTranscript(String.join("\n", context.getConversationHistory()))
                .conversationSummary(context.getConversationSummary())
                .totalTurnCount(context.getTotalTurnCount())
                .slotStateSnapshot(context.getSlotState())
                .lastAskedSlots(new ArrayList<>(context.getLastAskedSlots()))
                .build();
    }

    private static String resolveLatestUserInput(TriageContext context) {
        if (context.getLatestUserTurn() != null && !context.getLatestUserTurn().isBlank()) {
            return context.getLatestUserTurn();
        }
        return context.getUserInput();
    }
}
