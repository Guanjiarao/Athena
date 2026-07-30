

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.CorrectionUnderstanding;
import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.StateReducerResult;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizationAgentResult {

    private NormalizedTurn normalizedTurn;

    private TurnUnderstanding latestTurnUnderstanding;

    @Builder.Default
    private List<Fact> factHistory = new ArrayList<>();

    @Builder.Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    private String finalPrimaryComplaint;

    private StateReducerResult latestStateReducerResult;

    @Builder.Default
    private List<StateReducerResult> stateReducerHistory = new ArrayList<>();

    @Builder.Default
    private List<RiskSignalUnderstanding> riskSignalState = new ArrayList<>();

    @Builder.Default
    private List<CorrectionUnderstanding> correctionHistory = new ArrayList<>();
}
