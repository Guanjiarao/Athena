

package com.nageoffer.ai.ragent.triage.rule;

import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.QuestionGapSource;
import com.nageoffer.ai.ragent.triage.model.QuestionGapType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 规则模块入口，仅负责 Redis/DB 规则查询，不调用 LLM。
 */
@Component
@RequiredArgsConstructor
public class RuleAgent {

    private final TriageSlotRuleService triageSlotRuleService;

    @RagTraceNode(name = "RuleAgent", type = "TRIAGE_RULE")
    public RuleAgentResult lookup(RuleLookupRequest request) {
        List<String> signals = normalizeSignals(request == null ? null : request.getSignals());
        List<MatchedSlotRule> matchedRules = new ArrayList<>();
        for (String signal : signals) {
            List<SlotRuleDefinition> rules = triageSlotRuleService.getRulesBySignal(signal);
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            matchedRules.addAll(rules.stream()
                    .map(MatchedSlotRule::from)
                    .toList());
        }
        return RuleAgentResult.builder()
                .searchedSignals(signals)
                .matchedRules(matchedRules)
                .ruleGaps(toRuleGaps(matchedRules))
                .options(collectOptions(matchedRules))
                .coldStartNeeded(matchedRules.isEmpty())
                .build();
    }

    private List<QuestionGap> toRuleGaps(List<MatchedSlotRule> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return List.of();
        }
        List<QuestionGap> gaps = new ArrayList<>();
        for (MatchedSlotRule rule : matchedRules) {
            if (rule == null || rule.getSlot() == null) {
                continue;
            }
            gaps.add(QuestionGap.builder()
                    .slot(rule.getSlot())
                    .gapType(rule.getGapType() == null ? QuestionGapType.FOLLOW_UP_REQUIRED : rule.getGapType())
                    .source(rule.getSource() == null ? QuestionGapSource.PATTERN : rule.getSource())
                    .priority(rule.getPriority() == null ? 60 : rule.getPriority())
                    .reason(rule.getReason())
                    .build());
        }
        return gaps;
    }

    private List<TriageClarificationData.QuestionOption> collectOptions(List<MatchedSlotRule> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return List.of();
        }
        List<TriageClarificationData.QuestionOption> options = new ArrayList<>();
        for (MatchedSlotRule rule : matchedRules) {
            if (rule != null && rule.getOptions() != null && !rule.getOptions().isEmpty()) {
                options.addAll(rule.getOptions());
            }
        }
        return options;
    }

    private List<String> normalizeSignals(List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String signal : signals) {
            if (signal != null && !signal.isBlank()) {
                normalized.add(signal.trim());
            }
        }
        return new ArrayList<>(normalized);
    }
}
