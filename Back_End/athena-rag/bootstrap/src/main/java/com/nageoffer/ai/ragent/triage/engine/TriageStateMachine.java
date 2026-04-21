/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.model.AuditLog;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.worker.RiskStratifierWorker;
import com.nageoffer.ai.ragent.triage.worker.SOPValidatorWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticParserWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TriageStateMachine {

    private static final int WARNING_THRESHOLD_LEVEL = 3;
    private static final Map<TriageState, Map<TriageEvent, TriageState>> TRANSITIONS = buildTransitions();

    private final SemanticParserWorker semanticParserWorker;
    private final SOPValidatorWorker sopValidatorWorker;
    private final RiskStratifierWorker riskStratifierWorker;
    private final TriageModelGateway triageModelGateway;
    private final Map<TriageState, StateHandler> stateHandlers;

    public TriageStateMachine(SemanticParserWorker semanticParserWorker,
                              SOPValidatorWorker sopValidatorWorker,
                              RiskStratifierWorker riskStratifierWorker,
                              TriageModelGateway triageModelGateway) {
        this.semanticParserWorker = semanticParserWorker;
        this.sopValidatorWorker = sopValidatorWorker;
        this.riskStratifierWorker = riskStratifierWorker;
        this.triageModelGateway = triageModelGateway;
        this.stateHandlers = buildStateHandlers();
    }

    public TriageState execute(TriageContext context) {
        if (context == null) {
            throw new IllegalArgumentException("TriageContext must not be null.");
        }
        context.ensureCollections();
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
        semanticParserWorker.execute(context);
        int symptomCount = context.getExtractedSymptoms() == null ? 0 : context.getExtractedSymptoms().size();
        return new TransitionDecision(TriageEvent.PARSE_SUCCESS,
                "Semantic parsing finished with " + symptomCount + " structured symptom(s).");
    }

    private TransitionDecision handleValidation(TriageContext context) {
        sopValidatorWorker.execute(context);
        if (context.hasMissingFields()) {
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            context.setFinalReply(buildClarificationReply(context));
            return new TransitionDecision(TriageEvent.MISSING_INFO,
                    "Missing mandatory fields: " + String.join(", ", context.getMissingFields()));
        }
        return new TransitionDecision(TriageEvent.INFO_COMPLETE, "All mandatory SOP fields are complete.");
    }

    private TransitionDecision handleRiskAssessment(TriageContext context) {
        riskStratifierWorker.execute(context);
        RiskLevel riskLevel = context.getRiskAssessment() == null
                ? RiskLevel.conservativeFallback("Risk result missing, fallback applied.").normalize()
                : context.getRiskAssessment().normalize();
        context.setRiskAssessment(riskLevel);
        if (riskLevel.getLevel() >= WARNING_THRESHOLD_LEVEL) {
            context.setNextAction(TriageAction.TRIGGER_WARNING);
            context.setFinalReply(buildWarningReply(context));
            return new TransitionDecision(TriageEvent.HIGH_RISK,
                    "High-risk threshold reached. level=" + riskLevel.getLevel()
                            + ", evidence=" + StrUtil.blankToDefault(riskLevel.getEvidence(), "N/A"));
        }
        return new TransitionDecision(TriageEvent.LOW_RISK,
                "Risk level accepted for report generation. level=" + riskLevel.getLevel());
    }

    private TransitionDecision handleReportGeneration(TriageContext context) {
        context.setNextAction(TriageAction.GENERATE_REPORT);
        context.setFinalReply(generatePreTriageReport(context));
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

    private String buildClarificationReply(TriageContext context) {
        List<String> missingFields = context.getMissingFields() == null ? Collections.emptyList() : context.getMissingFields();
        if (missingFields.isEmpty()) {
            return "为了继续判断，请再补充一些不适细节。";
        }
        List<String> prompts = new ArrayList<>();
        for (String field : missingFields) {
            if (StrUtil.isBlank(field)) {
                continue;
            }
            switch (field.trim()) {
                case "腹痛位置", "疼痛部位" -> prompts.add("肚子具体是哪个位置不舒服");
                case "疼痛性质" -> prompts.add("这种疼是绞痛、隐痛、刺痛，还是持续疼");
                case "是否伴随发热" -> prompts.add("有没有发热");
                case "主要症状" -> prompts.add("目前最明显的不适主要是什么");
                case "持续时间" -> prompts.add("这种不适是从什么时候开始的");
                case "是否伴随恶心或呕吐" -> prompts.add("有没有恶心、想吐或者已经吐过");
                case "是否伴随呼吸困难" -> prompts.add("有没有胸闷、气短或呼吸费力");
                case "体温" -> prompts.add("如果量过体温，体温大概是多少");
                default -> prompts.add(field.trim());
            }
        }
        return prompts.isEmpty() ? "为了继续判断，请再补充一些不适细节。" : "为了更准确判断，请再补充一下：" + String.join("；", prompts) + "。";
    }

    private String buildWarningReply(TriageContext context) {
        RiskLevel riskLevel = context.getRiskAssessment();
        String evidence = riskLevel == null ? "" : StrUtil.blankToDefault(riskLevel.getEvidence(), "");
        StringBuilder builder = new StringBuilder("根据当前症状描述，存在较高风险，建议尽快前往线下医院就诊。");
        if (StrUtil.isNotBlank(evidence)) {
            builder.append("重点依据：").append(evidence).append("。 ");
        }
        builder.append("如果症状持续加重，或出现呼吸困难、意识变化、明显出血等情况，请及时前往急诊。");
        return builder.toString().trim();
    }

    private String generatePreTriageReport(TriageContext context) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是医疗分诊系统中的报告生成助手。请用简洁中文输出适合手机端阅读的分诊摘要，不要给出明确诊断，不要输出 JSON。"));
            messages.add(ChatMessage.user(buildReportPrompt(context)));
            String report = triageModelGateway.chatWithReportModel(messages, 0.2D, 0.3D, 900);
            if (StrUtil.isNotBlank(report)) {
                return report.trim();
            }
        } catch (Exception ex) {
            log.warn("triage report generation failed, sessionId={}", context.getSessionId(), ex);
        }
        return buildFallbackReport(context);
    }

    private String buildReportPrompt(TriageContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话ID：").append(context.getSessionId()).append("\n");
        builder.append("用户描述：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("结构化症状：\n");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("- 暂无可用结构化症状\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                builder.append("- 症状：").append(StrUtil.blankToDefault(symptom.getName(), "未知症状")).append("\n");
                if (StrUtil.isNotBlank(symptom.getBodyPart())) builder.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (StrUtil.isNotBlank(symptom.getDuration())) builder.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (StrUtil.isNotBlank(symptom.getSeverity())) builder.append("  程度：").append(symptom.getSeverity()).append("\n");
                if (CollUtil.isNotEmpty(symptom.getCharacteristics())) builder.append("  特征：").append(String.join("、", symptom.getCharacteristics())).append("\n");
                if (CollUtil.isNotEmpty(symptom.getAccompanyingSymptoms())) builder.append("  伴随症状：").append(String.join("、", symptom.getAccompanyingSymptoms())).append("\n");
            }
        }
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            builder.append("风险等级：").append(riskLevel.getLevel()).append("\n");
            builder.append("风险分数：").append(riskLevel.getScore()).append("\n");
            builder.append("依据：").append(StrUtil.blankToDefault(riskLevel.getEvidence(), "暂无")).append("\n");
            builder.append("解释：").append(StrUtil.blankToDefault(riskLevel.getRationale(), "暂无")).append("\n");
        }
        return builder.toString();
    }

    private String buildFallbackReport(TriageContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("【分诊摘要】\n");
        builder.append("主要不适：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("症状概览：");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("当前可用信息有限，仍需继续补充描述。\n");
        } else {
            List<String> lines = new ArrayList<>();
            for (Symptom symptom : context.getExtractedSymptoms()) {
                StringBuilder line = new StringBuilder(StrUtil.blankToDefault(symptom.getName(), "症状"));
                if (StrUtil.isNotBlank(symptom.getBodyPart())) line.append("（").append(symptom.getBodyPart()).append("）");
                if (StrUtil.isNotBlank(symptom.getDuration())) line.append("，持续").append(symptom.getDuration());
                if (StrUtil.isNotBlank(symptom.getSeverity())) line.append("，程度").append(symptom.getSeverity());
                lines.add(line.toString());
            }
            builder.append(String.join("；", lines)).append("。\n");
        }
        if (context.getRiskAssessment() != null) {
            builder.append("风险等级：").append(context.getRiskAssessment().getLevel()).append("，依据：")
                    .append(StrUtil.blankToDefault(context.getRiskAssessment().getEvidence(), "暂无")).append("\n");
        }
        builder.append("建议：本结果仅用于分诊辅助，不能替代线下面诊，如症状持续或加重，请及时就医。");
        return builder.toString();
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

    private record TransitionDecision(TriageEvent event, String rationale) {}
}