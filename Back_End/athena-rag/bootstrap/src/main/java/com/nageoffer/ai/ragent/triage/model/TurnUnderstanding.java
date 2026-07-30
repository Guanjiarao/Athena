

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
public class TurnUnderstanding {

    private TurnIntent intent;

    private ComplaintUnderstanding primaryComplaint;

    @Default
    private List<AnsweredSlotUnderstanding> answeredSlots = new ArrayList<>();

    @Default
    private List<RiskSignalUnderstanding> riskSignals = new ArrayList<>();

    @Default
    private List<CorrectionUnderstanding> corrections = new ArrayList<>();

    @Default
    private List<String> notes = new ArrayList<>();

    private Double confidence;
}
