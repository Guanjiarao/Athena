

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 入口语义归一化 Agent。
 *
 * <p>NormalizationAgent 是 normalization 包的对外边界：输入为当前轮上下文/快照，输出为
 * NormalizedTurn。第一阶段仍按旧顺序包裹 TurnUnderstandingWorker、SemanticParserWorker、FactExtractor，
 * 保持 worker 的既有副作用和行为不变。</p>
 */
@Component
@RequiredArgsConstructor
public class NormalizationAgent {

    private final TurnUnderstandingWorker turnUnderstandingWorker;
    private final SemanticParserWorker semanticParserWorker;
    private final FactExtractor factExtractor;

    public NormalizedTurn normalize(TriageContext context) {
        TurnInputSnapshot snapshot = TurnInputSnapshot.from(context);
        return normalize(context, snapshot);
    }

    public NormalizedTurn normalize(TriageContext context, TurnInputSnapshot snapshot) {
        if (context == null) {
            return NormalizedTurn.builder().build();
        }
        context.ensureCollections();
        turnUnderstandingWorker.execute(context);
        semanticParserWorker.execute(context);
        factExtractor.execute(context);
        return NormalizedTurn.builder()
                .turnUnderstanding(context.getLatestTurnUnderstanding())
                .primaryComplaint(context.getFinalPrimaryComplaint())
                .signals(collectSignals(context, snapshot))
                .symptoms(context.getExtractedSymptoms() == null ? new ArrayList<>() : new ArrayList<>(context.getExtractedSymptoms()))
                .facts(context.getFactHistory() == null ? new ArrayList<>() : new ArrayList<>(context.getFactHistory()))
                .answeredSlots(collectAnsweredSlots(context.getFactHistory()))
                .build();
    }

    private List<String> collectSignals(TriageContext context, TurnInputSnapshot snapshot) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        if (context.getFinalPrimaryComplaint() != null && !context.getFinalPrimaryComplaint().isBlank()) {
            signals.add(context.getFinalPrimaryComplaint().trim());
        }
        if (context.getExtractedSymptoms() != null) {
            context.getExtractedSymptoms().forEach(symptom -> {
                if (symptom != null && symptom.getName() != null && !symptom.getName().isBlank()) {
                    signals.add(symptom.getName().trim());
                }
            });
        }
        if (signals.isEmpty() && snapshot != null
                && snapshot.getLatestUserInput() != null && !snapshot.getLatestUserInput().isBlank()) {
            signals.add(snapshot.getLatestUserInput().trim());
        }
        return new ArrayList<>(signals);
    }

    private List<SlotCode> collectAnsweredSlots(List<Fact> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<SlotCode> slots = new LinkedHashSet<>();
        for (Fact fact : facts) {
            if (fact != null && fact.getSlot() != null) {
                slots.add(fact.getSlot());
            }
        }
        return new ArrayList<>(slots);
    }
}
