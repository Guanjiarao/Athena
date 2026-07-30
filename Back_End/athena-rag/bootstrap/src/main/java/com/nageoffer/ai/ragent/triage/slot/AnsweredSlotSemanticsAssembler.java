

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.CorrectionUnderstanding;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnIntent;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

public class AnsweredSlotSemanticsAssembler {
    private final AnsweredSlotFlowCollector answeredSlotFlowCollector;
    private final CorrectionPhraseParser correctionPhraseParser;
    private final CorrectionTargetResolver correctionTargetResolver;

    public AnsweredSlotSemanticsAssembler(AnsweredSlotFlowCollector answeredSlotFlowCollector,
                                          CorrectionPhraseParser correctionPhraseParser,
                                          CorrectionTargetResolver correctionTargetResolver) {
        this.answeredSlotFlowCollector = answeredSlotFlowCollector;
        this.correctionPhraseParser = correctionPhraseParser;
        this.correctionTargetResolver = correctionTargetResolver;
    }

    public void apply(TriageContext context,
                      TurnUnderstanding understanding,
                      String text,
                      ComplaintUnderstanding explicitComplaint) {
        if (understanding == null) {
            return;
        }
        deriveRiskSignalsFromAnswers(understanding);
        assembleCorrection(context, understanding, text, explicitComplaint);
        repairIntent(context, understanding, text, explicitComplaint);
    }

    private void deriveRiskSignalsFromAnswers(TurnUnderstanding understanding) {
        if (understanding.getAnsweredSlots() == null) return;
        for (AnsweredSlotUnderstanding answered : understanding.getAnsweredSlots()) {
            RiskSignalType type = mapRiskSignal(answered == null ? null : answered.getSlot());
            if (type == null || answered == null) continue;
            AssertionStatus assertion = answered.getAssertion();
            if (assertion != AssertionStatus.PRESENT && assertion != AssertionStatus.UNKNOWN && assertion != AssertionStatus.SUSPECTED) continue;
            boolean exists = understanding.getRiskSignals().stream().anyMatch(signal -> signal != null && signal.getType() == type);
            if (!exists) understanding.getRiskSignals().add(RiskSignalUnderstanding.builder().type(type).assertion(assertion == AssertionStatus.PRESENT ? AssertionStatus.PRESENT : AssertionStatus.SUSPECTED).confidence(answered.getConfidence()).evidence(answered.getEvidence()).build());
        }
    }

    private void assembleCorrection(TriageContext context,
                                    TurnUnderstanding understanding,
                                    String text,
                                    ComplaintUnderstanding explicitComplaint) {
        if (!understanding.getCorrections().isEmpty()) return;
        CorrectionPhraseParser.ParsedCorrectionPhrase phrase = correctionPhraseParser.parse(text);
        if (phrase == null) return;
        CorrectionTargetResolver.ResolvedCorrection resolved = correctionTargetResolver.resolve(context, phrase, explicitComplaint, text);
        CorrectionUnderstanding correction = toCorrectionUnderstanding(resolved);
        if (correction != null) understanding.getCorrections().add(correction);
    }

    private void repairIntent(TriageContext context,
                              TurnUnderstanding understanding,
                              String text,
                              ComplaintUnderstanding explicitComplaint) {
        if (understanding.getIntent() != null && understanding.getIntent() != TurnIntent.UNKNOWN) return;
        if (correctionPhraseParser.hasCorrectionCue(text) || !understanding.getCorrections().isEmpty()) {
            understanding.setIntent(TurnIntent.CORRECTION);
            return;
        }
        if (!understanding.getAnsweredSlots().isEmpty() || answeredSlotFlowCollector.answersLastAsked(context, text)) {
            understanding.setIntent(TurnIntent.ANSWER_FOLLOW_UP);
            return;
        }
        if (explicitComplaint != null && !blank(explicitComplaint.getValue())) {
            understanding.setIntent(TurnIntent.NEW_COMPLAINT);
            return;
        }
        if (text != null && text.length() <= 4) understanding.setIntent(TurnIntent.WEAK_INPUT);
    }

    private CorrectionUnderstanding toCorrectionUnderstanding(CorrectionTargetResolver.ResolvedCorrection resolved) {
        if (resolved == null || blank(resolved.confirmValue())) return null;
        return CorrectionUnderstanding.builder().target(resolved.target()).slot(resolved.slot()).rejectValue(trim(resolved.rejectValue())).confirmValue(trim(resolved.confirmValue())).confidence(0.85D).evidence(resolved.evidence()).build();
    }

    private RiskSignalType mapRiskSignal(SlotCode slot) {
        return slot == SlotCode.DYSPNEA_PRESENCE ? RiskSignalType.DYSPNEA : slot == SlotCode.BLEEDING_PRESENCE ? RiskSignalType.BLEEDING : slot == SlotCode.SEIZURE_PRESENCE ? RiskSignalType.SEIZURE : null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }
}
