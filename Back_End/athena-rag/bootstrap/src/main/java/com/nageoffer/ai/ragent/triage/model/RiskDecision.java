

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskDecision {

    private RiskDecisionType decisionType;

    private RiskLevel finalRiskLevel;

    private String decisionReason;

    private Boolean shouldInterrupt;

    private Boolean needsMoreInfo;

    @Default
    private List<RiskSignalUnderstanding> signals = new ArrayList<>();

    @Default
    private List<RiskGap> confirmedRiskGaps = new ArrayList<>();

    @Default
    private List<RiskGap> suspectedRiskGaps = new ArrayList<>();

    @Default
    private List<RiskGap> unresolvedRiskGaps = new ArrayList<>();

    @Default
    private List<String> evidence = new ArrayList<>();
}
