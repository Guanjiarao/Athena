

package com.nageoffer.ai.ragent.triage.rule;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule Agent Redis/DB lookup 结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleAgentResult {

    @Builder.Default
    private List<String> searchedSignals = new ArrayList<>();

    @Builder.Default
    private List<MatchedSlotRule> matchedRules = new ArrayList<>();

    @Builder.Default
    private List<QuestionGap> ruleGaps = new ArrayList<>();

    @Builder.Default
    private List<TriageClarificationData.QuestionOption> options = new ArrayList<>();

    /**
     * Explicit cold-start signal for Supervisor/Planner. True when no reusable Redis/DB rule was found.
     */
    private Boolean coldStartNeeded;

    public boolean hasMatchedRules() {
        return matchedRules != null && !matchedRules.isEmpty();
    }
}
