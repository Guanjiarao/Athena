

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

/**
 * Structured follow-up planning result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionPlan {

    @Default
    private List<SlotCode> nextSlotsToAsk = new ArrayList<>();

    @Default
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Default
    private List<QuestionNeed> questionNeeds = new ArrayList<>();

    @Default
    private List<QuestionGap> selectedQuestionGaps = new ArrayList<>();

    @Default
    private List<QuestionGap> suppressedQuestionGaps = new ArrayList<>();

    @Default
    private List<AskabilityDecision> askabilityDecisions = new ArrayList<>();

    private String priorityReason;

    private String policyReason;

    private Integer askCount;

    private Boolean followUpMode;
}
