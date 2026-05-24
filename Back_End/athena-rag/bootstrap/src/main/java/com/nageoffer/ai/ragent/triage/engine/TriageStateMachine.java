

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.triage.agent.TriageSupervisor;
import com.nageoffer.ai.ragent.triage.session.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.model.AuditLog;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskDecisionType;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import com.nageoffer.ai.ragent.triage.question.QuestionPlanningSupport;
import com.nageoffer.ai.ragent.triage.risk.RiskHeuristicHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 分诊状态机只负责状态流转和终态动作选择。
 *
 * <p>语义归一化、规则检索、槽位更新、风险评估和问题规划统一由 TriageSupervisor 编排；
 * 状态机消费 Supervisor 已写入的结果，不再直接调用旧 parsing/slot/risk/question worker。</p>
 */
@Slf4j
@Component
public class TriageStateMachine {

    private static final int WARNING_THRESHOLD_LEVEL = 3;
    private static final Map<TriageState, Map<TriageEvent, TriageState>> TRANSITIONS = buildTransitions();

    private final RiskHeuristicHelper riskHeuristicHelper;
    private final TriageModelGateway triageModelGateway;
    private final TriageSessionProperties triageSessionProperties;
    private final TurnLimitHelper turnLimitHelper;
    private final QuestionOptionProvider optionGenerator;
    private final QuestionPlanningSupport questionPlanSupport;
    private final TriageSupervisor triageSupervisor;
    private final Map<TriageState, StateHandler> stateHandlers;

    public TriageStateMachine(TriageModelGateway triageModelGateway,
                              RiskHeuristicHelper riskHeuristicHelper,
                              TriageSessionProperties triageSessionProperties,
                              TurnLimitHelper turnLimitHelper,
                              QuestionOptionProvider optionGenerator,
                              QuestionPlanningSupport questionPlanSupport,
                              TriageSupervisor triageSupervisor) {
        this.triageModelGateway = triageModelGateway;
        this.riskHeuristicHelper = riskHeuristicHelper;
        this.triageSessionProperties = triageSessionProperties;
        this.turnLimitHelper = turnLimitHelper;
        this.optionGenerator = optionGenerator;
        this.questionPlanSupport = questionPlanSupport;
        this.triageSupervisor = triageSupervisor;
        this.stateHandlers = buildStateHandlers();
    }

    @RagTraceNode(name = "TriageStateMachine", type = "TRIAGE_STATE")
    public TriageState execute(TriageContext context) {
        if (context == null) {
            throw new IllegalArgumentException("TriageContext must not be null.");
        }
        context.ensureCollections();
        if (turnLimitHelper.shouldForceReport(context)) {
            context.setCurrentState(TriageState.REPORT_GENERATING);
            context.setNextAction(TriageAction.GENERATE_REPORT);
            context.setPendingSlots(List.of());
            context.setMissingFields(List.of());
            context.setQuestionPlan(QuestionPlan.builder()
                    .nextSlotsToAsk(List.of())
                    .pendingSlots(List.of())
                    .askCount(0)
                    .followUpMode(false)
                    .priorityReason("达到最大轮次，强制生成报告。")
                    .policyReason("maxTotalTurns=" + triageSessionProperties.getMaxTotalTurns())
                    .build());
            context.setFinalReply(TriageReplyBuilder.generatePreTriageReport(context, triageModelGateway));
            context.appendState("Max turn guard triggered before state machine: totalTurnCount="
                    + context.getTotalTurnCount() + ", maxTotalTurns=" + triageSessionProperties.getMaxTotalTurns());
            return TriageState.COMPLETED;
        }
        TriageState currentState = TriageState.INIT;
        context.setCurrentState(currentState);
        currentState = moveTo(context, currentState,
                new TransitionDecision(TriageEvent.START_ANALYSIS, "Session entered the triage state machine."));
        try {
            while (!currentState.isTerminal()) {
                StateHandler handler = stateHandlers.get(currentState);
                if (handler == null) {
                    throw new IllegalStateException("No handler configured for state " + currentState);
                }
                currentState = moveTo(context, currentState, handler.handle(context));
            }
            return currentState;
        } catch (ClientException ex) {
            moveTo(context, context.getCurrentState() == null ? currentState : context.getCurrentState(),
                    new TransitionDecision(TriageEvent.FAILURE, "Client exception interrupted triage: " + ex.getMessage()));
            throw ex;
        } catch (Exception ex) {
            log.error("triage state machine failed, sessionId={}", context.getSessionId(), ex);
            applyFailureFallback(context);
            return moveTo(context, context.getCurrentState() == null ? currentState : context.getCurrentState(),
                    new TransitionDecision(TriageEvent.FAILURE, "Unexpected failure interrupted triage: " + ex.getMessage()));
        }
    }

    public TriageState nextState(TriageState currentState, TriageEvent event) {
        Map<TriageEvent, TriageState> candidates = TRANSITIONS.get(currentState);
        if (candidates == null || !candidates.containsKey(event)) {
            throw new IllegalStateException("No transition configured for state=" + currentState + ", event=" + event);
        }
        return candidates.get(event);
    }

    private Map<TriageState, StateHandler> buildStateHandlers() {
        EnumMap<TriageState, StateHandler> handlers = new EnumMap<>(TriageState.class);
        handlers.put(TriageState.PARSING, this::handleParsing);
        handlers.put(TriageState.VALIDATING, this::handleValidation);
        handlers.put(TriageState.RISK_ASSESSING, this::handleRiskAssessment);
        handlers.put(TriageState.REPORT_GENERATING, this::handleReportGeneration);
        return handlers;
    }

    private TransitionDecision handleParsing(TriageContext context) {
        triageSupervisor.runUnderstandingAndAgents(context);
        return new TransitionDecision(
                TriageEvent.PARSE_SUCCESS,
                "Triage supervisor finished normalization, rule lookup, slot reduction, and risk assessment."
        );
    }

    private TransitionDecision handleValidation(TriageContext context) {
        RiskDecision parsingRiskDecision = context.getRiskDecision();
        RiskLevel parsingRiskLevel = context.getRiskAssessment() == null ? null : context.getRiskAssessment().normalize();
        if (isRiskInterrupt(parsingRiskDecision, parsingRiskLevel)) {
            context.setNextAction(TriageAction.TRIGGER_WARNING);
            context.setFinalReply(TriageReplyBuilder.buildWarningReply(context));
            return new TransitionDecision(
                    TriageEvent.INFO_COMPLETE,
                    "Risk interrupt detected after supervisor; skipping normal question planning."
            );
        }

        String rationale = TriageStageExecutor.executeValidation(context, optionGenerator, questionPlanSupport, triageModelGateway);
        boolean needsClarification = context.getPendingSlots() != null && !context.getPendingSlots().isEmpty();
        boolean shouldFastTrackHighRisk = riskHeuristicHelper.shouldFastTrackHighRisk(context);

        // 检查是否触发硬红旗快速通道（优先级最高）
        if (needsClarification && shouldFastTrackHighRisk) {
            return new TransitionDecision(
                    TriageEvent.INFO_COMPLETE,
                    rationale + " Fast-tracked to risk assessment due to hard red-flag evidence."
            );
        }

        // 新增：检查是否满足最少轮次要求
        boolean hasMetMinTurns = turnLimitHelper.hasMetMinimumTurns(context);

        // 如果未满足最少轮次，仅在已有明确待问槽位和回复时继续提问；否则进入风险评估，避免空计划落到通用兜底话术。
        if (!hasMetMinTurns) {
            boolean hasConcreteClarification = needsClarification && StrUtil.isNotBlank(context.getFinalReply());
            if (hasConcreteClarification) {
                return new TransitionDecision(
                        TriageEvent.MISSING_INFO,
                        rationale + " Minimum turn requirement not met (current: "
                                + context.getTotalTurnCount() + ", required: "
                                + triageSessionProperties.getMinRequiredTurns() + "), continuing clarification with concrete finalReply="
                                + context.getFinalReply()
                );
            }
            log.warn("[StateMachine] 最少轮次未满足但没有明确待问槽位或澄清回复，跳过强制澄清进入风险评估。sessionId={}, needsClarification={}, pendingSlots={}, finalReply={}",
                    context.getSessionId(), needsClarification, context.getPendingSlots(), context.getFinalReply());
        }

        // 检查是否达到最大轮次限制
        if (needsClarification && turnLimitHelper.shouldForceReport(context)) {
            return new TransitionDecision(
                    TriageEvent.INFO_COMPLETE,
                    rationale + " Turn limit reached, forcing report generation."
            );
        }

        // 正常流程：根据槽位是否填充完成
        return new TransitionDecision(
                needsClarification ? TriageEvent.MISSING_INFO : TriageEvent.INFO_COMPLETE,
                rationale
        );
    }

    private TransitionDecision handleRiskAssessment(TriageContext context) {
        if (isRiskInterrupt(context.getRiskDecision(), context.getRiskAssessment())) {
            RiskLevel riskLevel = context.getRiskAssessment() == null
                    ? RiskLevel.conservativeFallback("Risk interrupt without risk level, fallback applied.").normalize()
                    : context.getRiskAssessment().normalize();
            context.setRiskAssessment(riskLevel);
            context.setNextAction(TriageAction.TRIGGER_WARNING);
            context.setFinalReply(TriageReplyBuilder.buildWarningReply(context));
            return new TransitionDecision(TriageEvent.HIGH_RISK,
                    "High-risk interrupt reused from supervisor risk assessment.");
        }
        RiskLevel riskLevel = context.getRiskAssessment() == null
                ? RiskLevel.conservativeFallback("Risk result missing after supervisor, fallback applied.").normalize()
                : context.getRiskAssessment().normalize();
        context.setRiskAssessment(riskLevel);
        RiskDecision riskDecision = context.getRiskDecision();
        if (riskDecision != null) {
            if (riskDecision.getDecisionType() == RiskDecisionType.TRIGGER_WARNING
                    || Boolean.TRUE.equals(riskDecision.getShouldInterrupt())
                    || riskLevel.getLevel() >= WARNING_THRESHOLD_LEVEL) {
                context.setNextAction(TriageAction.TRIGGER_WARNING);
                context.setFinalReply(TriageReplyBuilder.buildWarningReply(context));
                return new TransitionDecision(TriageEvent.HIGH_RISK,
                        "High-risk decision reached. type=" + riskDecision.getDecisionType()
                                + ", evidence=" + StrUtil.blankToDefault(riskLevel.getEvidence(), "N/A"));
            }
            if ((riskDecision.getDecisionType() == RiskDecisionType.ASK_RISK_CLARIFICATION
                    || riskDecision.getDecisionType() == RiskDecisionType.ESCALATE_FROM_HISTORY
                    || Boolean.TRUE.equals(riskDecision.getNeedsMoreInfo()))
                    && context.getQuestionPlan() != null
                    && context.getQuestionPlan().getNextSlotsToAsk() != null
                    && !context.getQuestionPlan().getNextSlotsToAsk().isEmpty()) {
                if (turnLimitHelper.shouldForceReport(context)) {
                    return new TransitionDecision(TriageEvent.LOW_RISK,
                            "Turn limit reached, forcing report generation despite risk decision wanting more info.");
                }
                context.setNextAction(TriageAction.ASK_CLARIFICATION);
                String clarificationReply = TriageReplyBuilder.buildClarificationReply(context, optionGenerator, questionPlanSupport, triageModelGateway);
                log.info("[StateMachine] 澄清分支生成 finalReply: {}", clarificationReply);
                context.setFinalReply(clarificationReply);
                log.info("[StateMachine] 澄清分支写入 context 后 finalReply: {}", context.getFinalReply());

                // 检查是否需要强制生成报告（LLM智能兜底决策）
                if (Boolean.TRUE.equals(context.getForceGenerateReport())) {
                    log.info("[StateMachine] LLM智能兜底决策：强制生成报告, 理由: {}", context.getForceGenerateReportReason());
                    context.setNextAction(TriageAction.GENERATE_REPORT);
                    context.setFinalReply(TriageReplyBuilder.generatePreTriageReport(context, triageModelGateway));
                    log.info("[StateMachine] 强制报告分支 finalReply: {}", context.getFinalReply());
                    return new TransitionDecision(TriageEvent.LOW_RISK,
                            "LLM emergency fallback: force report generation. Reason: " + context.getForceGenerateReportReason());
                }

                return new TransitionDecision(TriageEvent.MISSING_INFO,
                        "Risk decision requires another clarification round before final output. finalReply=" + context.getFinalReply());
            }
            return new TransitionDecision(TriageEvent.LOW_RISK,
                    "Risk decision accepted for report generation. type=" + riskDecision.getDecisionType());
        }
        if (Boolean.TRUE.equals(riskLevel.getShouldInterrupt()) || riskLevel.getLevel() >= WARNING_THRESHOLD_LEVEL) {
            context.setNextAction(TriageAction.TRIGGER_WARNING);
            context.setFinalReply(TriageReplyBuilder.buildWarningReply(context));
            return new TransitionDecision(TriageEvent.HIGH_RISK,
                    "High-risk threshold reached. level=" + riskLevel.getLevel()
                            + ", evidence=" + StrUtil.blankToDefault(riskLevel.getEvidence(), "N/A"));
        }
        if (Boolean.TRUE.equals(riskLevel.getNeedsMoreInfo()) && context.getQuestionPlan() != null && context.getQuestionPlan().getNextSlotsToAsk() != null && !context.getQuestionPlan().getNextSlotsToAsk().isEmpty()) {
            if (turnLimitHelper.shouldForceReport(context)) {
                return new TransitionDecision(TriageEvent.LOW_RISK,
                        "Turn limit reached, forcing report generation despite risk level wanting more info.");
            }
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            String clarificationReply = TriageReplyBuilder.buildClarificationReply(context, optionGenerator, questionPlanSupport, triageModelGateway);
            log.info("[StateMachine] 风险澄清分支生成 finalReply: {}", clarificationReply);
            context.setFinalReply(clarificationReply);
            log.info("[StateMachine] 风险澄清分支写入 context 后 finalReply: {}", context.getFinalReply());

            // 检查是否需要强制生成报告（LLM智能兜底决策）
            if (Boolean.TRUE.equals(context.getForceGenerateReport())) {
                log.info("[StateMachine] LLM智能兜底决策：强制生成报告, 理由: {}", context.getForceGenerateReportReason());
                context.setNextAction(TriageAction.GENERATE_REPORT);
                context.setFinalReply(TriageReplyBuilder.generatePreTriageReport(context, triageModelGateway));
                log.info("[StateMachine] 强制报告分支 finalReply: {}", context.getFinalReply());
                return new TransitionDecision(TriageEvent.LOW_RISK,
                        "LLM emergency fallback: force report generation. Reason: " + context.getForceGenerateReportReason());
            }

            return new TransitionDecision(TriageEvent.MISSING_INFO,
                    "Risk assessment prefers another clarification round before final output. finalReply=" + context.getFinalReply());
        }
        return new TransitionDecision(TriageEvent.LOW_RISK,
                "Risk level accepted for report generation. level=" + riskLevel.getLevel());
    }

    private boolean isRiskInterrupt(RiskDecision riskDecision, RiskLevel riskLevel) {
        RiskLevel normalizedRiskLevel = riskLevel == null ? null : riskLevel.normalize();
        return (riskDecision != null && (riskDecision.getDecisionType() == RiskDecisionType.TRIGGER_WARNING
                || Boolean.TRUE.equals(riskDecision.getShouldInterrupt())))
                || (normalizedRiskLevel != null
                && (Boolean.TRUE.equals(normalizedRiskLevel.getShouldInterrupt())
                || normalizedRiskLevel.getLevel() >= WARNING_THRESHOLD_LEVEL));
    }

    private TransitionDecision handleReportGeneration(TriageContext context) {
        context.setNextAction(TriageAction.GENERATE_REPORT);
        context.setFinalReply(TriageReplyBuilder.generatePreTriageReport(context, triageModelGateway));
        return new TransitionDecision(TriageEvent.REPORT_READY, "Pre-triage report generated for downstream rendering.");
    }

    private TriageState moveTo(TriageContext context, TriageState currentState, TransitionDecision decision) {
        TriageState nextState = nextState(currentState, decision.event);
        context.setCurrentState(nextState);
        context.appendAudit(AuditLog.builder().timestamp(Instant.now()).previousState(currentState)
                .triggerEvent(decision.event).currentState(nextState).decisionBasis(decision.rationale).build());
        context.appendState("Transition: " + currentState + " --" + decision.event + "--> " + nextState + " | " + decision.rationale);
        return nextState;
    }

    private void applyFailureFallback(TriageContext context) {
        context.setRiskAssessment(RiskLevel.conservativeFallback("系统执行异常，自动降级为补充信息模式。").normalize());
        context.setNextAction(TriageAction.ASK_CLARIFICATION);
        context.setFinalReply("系统当前较忙，请稍后重试，并尽量补充主要不适、持续时间和具体部位。");
    }

    private static Map<TriageState, Map<TriageEvent, TriageState>> buildTransitions() {
        EnumMap<TriageState, Map<TriageEvent, TriageState>> transitions = new EnumMap<>(TriageState.class);
        register(transitions, TriageState.INIT, TriageEvent.START_ANALYSIS, TriageState.PARSING);
        register(transitions, TriageState.INIT, TriageEvent.FAILURE, TriageState.INTERRUPTED);
        register(transitions, TriageState.PARSING, TriageEvent.PARSE_SUCCESS, TriageState.VALIDATING);
        register(transitions, TriageState.PARSING, TriageEvent.FAILURE, TriageState.INTERRUPTED);
        register(transitions, TriageState.VALIDATING, TriageEvent.MISSING_INFO, TriageState.INTERRUPTED);
        register(transitions, TriageState.VALIDATING, TriageEvent.INFO_COMPLETE, TriageState.RISK_ASSESSING);
        register(transitions, TriageState.VALIDATING, TriageEvent.FAILURE, TriageState.INTERRUPTED);
        register(transitions, TriageState.RISK_ASSESSING, TriageEvent.MISSING_INFO, TriageState.INTERRUPTED);
        register(transitions, TriageState.RISK_ASSESSING, TriageEvent.HIGH_RISK, TriageState.INTERRUPTED);
        register(transitions, TriageState.RISK_ASSESSING, TriageEvent.LOW_RISK, TriageState.REPORT_GENERATING);
        register(transitions, TriageState.RISK_ASSESSING, TriageEvent.FAILURE, TriageState.INTERRUPTED);
        register(transitions, TriageState.REPORT_GENERATING, TriageEvent.REPORT_READY, TriageState.COMPLETED);
        register(transitions, TriageState.REPORT_GENERATING, TriageEvent.FAILURE, TriageState.INTERRUPTED);
        return transitions;
    }

    private static void register(Map<TriageState, Map<TriageEvent, TriageState>> transitions,
                                 TriageState source, TriageEvent event, TriageState target) {
        transitions.computeIfAbsent(source, key -> new EnumMap<>(TriageEvent.class)).put(event, target);
    }

    @FunctionalInterface
    private interface StateHandler {
        TransitionDecision handle(TriageContext context);
    }

    private record TransitionDecision(TriageEvent event, String rationale) {
    }
}

