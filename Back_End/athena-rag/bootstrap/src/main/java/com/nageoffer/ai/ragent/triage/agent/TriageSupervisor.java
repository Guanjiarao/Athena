

package com.nageoffer.ai.ragent.triage.agent;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.normalization.NormalizationAgent;
import com.nageoffer.ai.ragent.triage.normalization.NormalizedTurn;
import com.nageoffer.ai.ragent.triage.risk.RiskAgent;
import com.nageoffer.ai.ragent.triage.risk.RiskAgentResult;
import com.nageoffer.ai.ragent.triage.question.ColdStartSlotSelector;
import com.nageoffer.ai.ragent.triage.question.QuestionPlanner;
import com.nageoffer.ai.ragent.triage.question.QuestionPlannerResult;
import com.nageoffer.ai.ragent.triage.rule.RuleAgentResult;
import com.nageoffer.ai.ragent.triage.rule.RuleLookupRequest;

import com.nageoffer.ai.ragent.triage.slot.SlotAgent;
import com.nageoffer.ai.ragent.triage.slot.SlotAgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 多 Agent 架构入口。
 *
 * <p>当前实现保持 Normalization 先跑，随后 Rule/Slot/Risk 并发执行。
 * 这些 Agent 仍包裹旧 worker，后续会继续演进为只返回 result、由 ContextReducer 单写 context。</p>
 */
@Slf4j
@Component
public class TriageSupervisor {

    private final NormalizationAgent normalizationAgent;
    private final com.nageoffer.ai.ragent.triage.rule.RuleAgent ruleAgent;
    private final SlotAgent slotAgent;
    private final RiskAgent riskAgent;
    private final TriageContextReducer contextReducer;
    private final ColdStartSlotSelector coldStartSlotSelector;
    private final QuestionPlanner questionPlanner;
    private final Executor triageAgentExecutor;

    public TriageSupervisor(NormalizationAgent normalizationAgent,
                            com.nageoffer.ai.ragent.triage.rule.RuleAgent ruleAgent,
                            SlotAgent slotAgent,
                            RiskAgent riskAgent,
                            TriageContextReducer contextReducer,
                            ColdStartSlotSelector coldStartSlotSelector,
                            QuestionPlanner questionPlanner,
                            @Qualifier("triageAgentExecutor") Executor triageAgentExecutor) {
        this.normalizationAgent = normalizationAgent;
        this.ruleAgent = ruleAgent;
        this.slotAgent = slotAgent;
        this.riskAgent = riskAgent;
        this.contextReducer = contextReducer;
        this.coldStartSlotSelector = coldStartSlotSelector;
        this.questionPlanner = questionPlanner;
        this.triageAgentExecutor = triageAgentExecutor;
    }

    public void runUnderstandingAndAgents(TriageContext context) {
        NormalizedTurn normalizedTurn = normalizationAgent.normalize(context);
        CompletableFuture<RuleAgentResult> ruleFuture = CompletableFuture
                .supplyAsync(() -> ruleAgent.lookup(RuleLookupRequest.builder()
                        .signals(normalizedTurn.getSignals())
                        .build()), triageAgentExecutor)
                .exceptionally(ex -> {
                    log.warn("[TriageSupervisor] RuleAgent failed, using empty lookup result. sessionId={}", context.getSessionId(), ex);
                    return RuleAgentResult.builder()
                            .searchedSignals(normalizedTurn.getSignals())
                            .coldStartNeeded(Boolean.TRUE)
                            .build();
                });
        CompletableFuture<SlotAgentResult> slotFuture = CompletableFuture
                .supplyAsync(() -> slotAgent.reduce(context, normalizedTurn), triageAgentExecutor)
                .exceptionally(ex -> {
                    log.warn("[TriageSupervisor] SlotAgent failed, using empty slot result. sessionId={}", context.getSessionId(), ex);
                    return SlotAgentResult.builder().build();
                });
        CompletableFuture<RiskAgentResult> riskFuture = CompletableFuture
                .supplyAsync(() -> riskAgent.assess(context, normalizedTurn), triageAgentExecutor)
                .exceptionally(ex -> {
                    log.warn("[TriageSupervisor] RiskAgent failed, using empty risk result. sessionId={}", context.getSessionId(), ex);
                    return RiskAgentResult.builder()
                            .interrupt(Boolean.FALSE)
                            .build();
                });
        RuleAgentResult ruleResult = ruleFuture.join();
        SlotAgentResult slotResult = slotFuture.join();
        RiskAgentResult riskResult = riskFuture.join();
        contextReducer.apply(context, normalizedTurn, riskResult, ruleResult, slotResult);
        if (ruleResult != null && Boolean.TRUE.equals(ruleResult.getColdStartNeeded())) {
            try {
                context.setPrefetchedColdStartQuestionPlan(prefetchColdStartQuestionPlan(context));
            } catch (Exception ex) {
                log.warn("[TriageSupervisor] ColdStart prefetch failed, skip prefetched plan. sessionId={}", context.getSessionId(), ex);
                context.setPrefetchedColdStartQuestionPlan(null);
            }
        } else {
            context.setPrefetchedColdStartQuestionPlan(null);
        }
        QuestionPlannerResult questionPlannerResult;
        try {
            questionPlannerResult = questionPlanner.execute(context);
        } catch (Exception ex) {
            log.warn("[TriageSupervisor] QuestionPlanner failed, using empty result. sessionId={}", context.getSessionId(), ex);
            questionPlannerResult = QuestionPlannerResult.builder().build();
        }
        contextReducer.applyQuestionPlan(context, questionPlannerResult);
        log.info("[TriageSupervisor] agents finished: sessionId={}, signals={}, rulesMatched={}, riskInterrupt={}",
                context.getSessionId(),
                normalizedTurn.getSignals(),
                ruleResult.hasMatchedRules(),
                riskResult.getInterrupt());
    }

    private QuestionPlan prefetchColdStartQuestionPlan(TriageContext context) {
        int consecutiveFallbackCount = countConsecutiveLLMFallbacks(context) + 1;
        log.info("[TriageSupervisor] RuleAgent miss, prefetch ColdStartSlotSelector. sessionId={}, consecutiveFallbackCount={}",
                context.getSessionId(), consecutiveFallbackCount);
        return coldStartSlotSelector.select(context, consecutiveFallbackCount);
    }

    private int countConsecutiveLLMFallbacks(TriageContext context) {
        if (context == null || context.getLlmFallbackHistory() == null || context.getLlmFallbackHistory().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = context.getLlmFallbackHistory().size() - 1; i >= 0; i--) {
            if (Boolean.TRUE.equals(context.getLlmFallbackHistory().get(i))) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
