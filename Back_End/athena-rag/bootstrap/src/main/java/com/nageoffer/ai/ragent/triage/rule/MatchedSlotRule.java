

package com.nageoffer.ai.ragent.triage.rule;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rule Agent 命中的槽位规则快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchedSlotRule {

    private String signal;

    private SlotCode slot;

    private QuestionGapType gapType;

    private QuestionGapSource source;

    private Integer priority;

    private String reason;

    private Double confidence;

    private List<TriageClarificationData.QuestionOption> options;

    static MatchedSlotRule from(SlotRuleDefinition rule) {
        if (rule == null) {
            return null;
        }
        return MatchedSlotRule.builder()
                .signal(rule.getSignal())
                .slot(rule.getSlot())
                .gapType(rule.getGapType())
                .source(rule.getSource())
                .priority(rule.getPriority())
                .reason(rule.getReason())
                .confidence(rule.getConfidence())
                .options(rule.getOptions())
                .build();
    }
}
