

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import com.nageoffer.ai.ragent.triage.normalization.SemanticParserSupport;

import java.util.ArrayList;
import java.util.List;

public class AnsweredSlotFlowCollector {
    private final SlotAnswerInferenceHelper slotAnswerInferenceHelper;

    public AnsweredSlotFlowCollector(SlotAnswerInferenceHelper slotAnswerInferenceHelper) {
        this.slotAnswerInferenceHelper = slotAnswerInferenceHelper;
    }

    public void collectInto(TriageContext context, TurnUnderstanding understanding, String text) {
        if (context == null || understanding == null || blank(text)) {
            return;
        }

        // 保存 LLM 已识别的槽位（关键修复：避免被候选槽位逻辑覆盖）
        List<AnsweredSlotUnderstanding> llmAnswered = understanding.getAnsweredSlots() != null
            ? new ArrayList<>(understanding.getAnsweredSlots())
            : new ArrayList<>();

        // 处理候选槽位（规则推理）
        List<SlotCode> candidates = candidateSlots(context);
        for (SlotCode slot : candidates) {
            AnsweredSlotUnderstanding inferred = slot == SlotCode.DURATION
                    ? inferDurationAnswer(text)
                    : slotAnswerInferenceHelper.infer(slot, text);
            if (inferred == null || hasSlot(understanding.getAnsweredSlots(), slot)) {
                continue;
            }
            inferred.setAnswersPreviousQuestion(Boolean.TRUE);
            understanding.getAnsweredSlots().add(inferred);
        }

        // 合并 LLM 识别的槽位（避免重复）
        for (AnsweredSlotUnderstanding llmSlot : llmAnswered) {
            if (llmSlot != null && llmSlot.getSlot() != null
                && !hasSlot(understanding.getAnsweredSlots(), llmSlot.getSlot())) {
                understanding.getAnsweredSlots().add(llmSlot);
            }
        }
    }

    public boolean answersLastAsked(TriageContext context, String text) {
        if (context == null || context.getLastAskedSlots() == null || blank(text)) {
            return false;
        }
        for (SlotCode slot : context.getLastAskedSlots()) {
            AnsweredSlotUnderstanding inferred = slot == SlotCode.DURATION
                    ? inferDurationAnswer(text)
                    : slotAnswerInferenceHelper.infer(slot, text);
            if (inferred != null) {
                return true;
            }
        }
        return false;
    }

    private AnsweredSlotUnderstanding inferDurationAnswer(String text) {
        String value = SemanticParserSupport.extractDuration(text);
        if (blank(value)) {
            return null;
        }
        return AnsweredSlotUnderstanding.builder()
                .slot(SlotCode.DURATION)
                .rawValue(value)
                .normalizedValue(value)
                .assertion(AssertionStatus.PRESENT)
                .confidence(0.85D)
                .evidence(text)
                .build();
    }

    private List<SlotCode> candidateSlots(TriageContext context) {
        List<SlotCode> candidates = new ArrayList<>();
        if (context.getLastAskedSlots() != null) {
            candidates.addAll(context.getLastAskedSlots());
        }
        if (context.getPendingSlots() != null) {
            for (SlotCode slot : context.getPendingSlots()) {
                if (!candidates.contains(slot)) {
                    candidates.add(slot);
                }
            }
        }
        return candidates;
    }

    private boolean hasSlot(List<AnsweredSlotUnderstanding> answeredSlots, SlotCode slot) {
        return answeredSlots != null && answeredSlots.stream().anyMatch(answered -> answered != null && answered.getSlot() == slot);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
