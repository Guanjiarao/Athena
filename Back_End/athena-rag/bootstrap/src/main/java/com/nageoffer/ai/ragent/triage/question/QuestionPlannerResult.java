

package com.nageoffer.ai.ragent.triage.question;

import com.nageoffer.ai.ragent.triage.model.AskabilityDecision;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result-only output of QuestionPlanner.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionPlannerResult {

    private QuestionPlan questionPlan;

    @Builder.Default
    private List<QuestionGap> candidateQuestionGaps = new ArrayList<>();

    @Builder.Default
    private List<QuestionGap> selectedQuestionGaps = new ArrayList<>();

    @Builder.Default
    private List<QuestionGap> suppressedQuestionGaps = new ArrayList<>();

    @Builder.Default
    private List<AskabilityDecision> askabilityDecisions = new ArrayList<>();

    @Builder.Default
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Builder.Default
    private List<SlotCode> lastAskedSlots = new ArrayList<>();

    @Builder.Default
    private List<Boolean> llmFallbackHistory = new ArrayList<>();

    private boolean llmFallbackTriggered;
}
