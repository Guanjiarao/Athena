

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StateReducerResult {

    @Default
    private Map<SlotCode, SlotValue> reducedSlots = new EnumMap<>(SlotCode.class);

    private ComplaintUnderstanding complaintTruth;

    @Default
    private List<SlotCode> answeredSlots = new ArrayList<>();

    @Default
    private List<SlotCode> pendingCandidates = new ArrayList<>();

    @Default
    private List<CorrectionUnderstanding> correctionLog = new ArrayList<>();

    @Default
    private List<RiskSignalUnderstanding> accumulatedRiskSignals = new ArrayList<>();
}
