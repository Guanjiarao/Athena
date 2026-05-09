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

package com.nageoffer.ai.ragent.triage.worker;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RiskStratifierWorker extends AbstractStructuredTriageWorker {
    private final RiskHeuristicHelper riskHeuristicHelper;
    public RiskStratifierWorker(LLMService llmService, ObjectMapper objectMapper, RiskHeuristicHelper riskHeuristicHelper) { super(llmService, objectMapper); this.riskHeuristicHelper = riskHeuristicHelper; }

    public TriageContext execute(TriageContext context) {
        if (context == null) context = new TriageContext();
        context.ensureCollections();
        RiskLevel hardRedFlag = riskHeuristicHelper.hardRedFlagFallback(context);
        if (hardRedFlag != null) { context.setRiskAssessment(hardRedFlag.normalize()); context.appendRiskDecision(buildRiskDecision(context, context.getRiskAssessment())); return context; }
        RiskLevel fallback = riskHeuristicHelper.heuristicRiskFallback(context), riskLevel;
        try { String rawResponse = invokeLlm(buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D); riskLevel = readObjectSafely(rawResponse, RiskLevel.class, fallback, "风险分层"); } catch (Exception ex) { riskLevel = fallback; }
        context.setRiskAssessment(riskLevel.normalize());
        context.appendRiskDecision(buildRiskDecision(context, context.getRiskAssessment()));
        return context;
    }

    private RiskDecision buildRiskDecision(TriageContext context, RiskLevel riskLevel) {
        RiskLevel normalized = riskLevel == null ? null : riskLevel.normalize();
        if (normalized == null) return null;
        List<RiskGap> unresolvedRiskGaps = buildUnresolvedRiskGaps(context, normalized);
        List<RiskGap> confirmedRiskGaps = buildConfirmedRiskGaps(context, normalized);
        List<RiskGap> suspectedRiskGaps = buildSuspectedRiskGaps(context, normalized, unresolvedRiskGaps, confirmedRiskGaps);
        RiskDecisionType decisionType = decideRiskAction(normalized, confirmedRiskGaps, suspectedRiskGaps, unresolvedRiskGaps);
        boolean shouldInterrupt = Boolean.TRUE.equals(normalized.getShouldInterrupt());
        boolean needsMoreInfo = Boolean.TRUE.equals(normalized.getNeedsMoreInfo()) || !unresolvedRiskGaps.isEmpty();
        String decisionReason = normalized.getRationale();
        List<String> evidence = normalized.getEvidence() == null || normalized.getEvidence().isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(normalized.getEvidence()));
        if (hasRecentUnresolvedHistory(context, 2) && (!unresolvedRiskGaps.isEmpty() || !suspectedRiskGaps.isEmpty()) && !shouldInterrupt) { decisionType = RiskDecisionType.ESCALATE_FROM_HISTORY; needsMoreInfo = true; decisionReason = decisionReason + " 历史上同类风险补问连续未闭合，当前按更保守策略升级。"; evidence.add("同类风险补问连续多轮未闭合。\n"); }
        else if (hasRecentSuspectedHistory(context, 2) && !suspectedRiskGaps.isEmpty() && unresolvedRiskGaps.isEmpty() && !shouldInterrupt) { decisionType = RiskDecisionType.ESCALATE_FROM_HISTORY; needsMoreInfo = true; decisionReason = decisionReason + " 疑似风险信号连续多轮存在，当前升级为优先补问风险。"; evidence.add("疑似风险信号连续多轮存在。\n"); }
        else if (hasRecentConfirmedHistory(context, 1) && !confirmedRiskGaps.isEmpty()) { decisionType = RiskDecisionType.TRIGGER_WARNING; shouldInterrupt = true; decisionReason = decisionReason + " 历史上已连续出现高风险确认信号，应维持中断策略。"; evidence.add("高风险确认信号跨轮持续存在。\n"); }
        return RiskDecision.builder().decisionType(decisionType).finalRiskLevel(normalized).decisionReason(decisionReason).shouldInterrupt(shouldInterrupt).needsMoreInfo(needsMoreInfo).signals(context.getRiskSignalState() == null ? new ArrayList<>() : new ArrayList<>(context.getRiskSignalState())).confirmedRiskGaps(confirmedRiskGaps).suspectedRiskGaps(suspectedRiskGaps).unresolvedRiskGaps(unresolvedRiskGaps).evidence(evidence).build();
    }

    private List<RiskGap> buildUnresolvedRiskGaps(TriageContext context, RiskLevel normalized) {
        Map<RiskSignalType, RiskGap> result = new LinkedHashMap<>();
        if (normalized.getMissingCriticalSlots() != null) for (SlotCode slotCode : normalized.getMissingCriticalSlots()) if (slotCode != null) result.put(mapSignalType(slotCode), RiskGap.builder().slot(slotCode).relatedSignalType(mapSignalType(slotCode)).signalStatus(RiskSignalStatus.UNRESOLVED).priority(95).reason("风险判断仍需要该关键槽位。").build());
        if (context.getRiskSignalState() != null) for (RiskSignalUnderstanding signal : context.getRiskSignalState()) {
            if (signal == null || signal.getType() == null) continue;
            if (signal.getAssertion() == AssertionStatus.UNKNOWN || signal.getAssertion() == AssertionStatus.SUSPECTED) result.putIfAbsent(signal.getType(), RiskGap.builder().slot(mapSlotCode(signal.getType())).relatedSignalType(signal.getType()).signalStatus(RiskSignalStatus.UNRESOLVED).priority(95).reason("风险信号已出现，但当前回答仍未完成确认。").build());
        }
        return new ArrayList<>(result.values());
    }

    private List<RiskGap> buildConfirmedRiskGaps(TriageContext context, RiskLevel normalized) {
        Map<RiskSignalType, RiskGap> result = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(normalized.getShouldInterrupt())) return new ArrayList<>();
        if (context.getRiskSignalState() != null) for (RiskSignalUnderstanding signal : context.getRiskSignalState()) if (signal != null && signal.getType() != null && signal.getAssertion() == AssertionStatus.PRESENT) result.put(signal.getType(), RiskGap.builder().slot(mapSlotCode(signal.getType())).relatedSignalType(signal.getType()).signalStatus(RiskSignalStatus.CONFIRMED).priority(100).reason(StrUtil.blankToDefault(signal.getEvidence(), "已命中高风险信号。")).build());
        if (result.isEmpty() && normalized.getRiskHints() != null) for (String riskHint : normalized.getRiskHints()) { RiskSignalType signalType = mapSignalHint(riskHint); if (signalType != null) result.put(signalType, RiskGap.builder().slot(mapSlotCode(signalType)).relatedSignalType(signalType).signalStatus(RiskSignalStatus.CONFIRMED).priority(100).reason("风险分层已确认该高危信号。").build()); }
        return new ArrayList<>(result.values());
    }

    private List<RiskGap> buildSuspectedRiskGaps(TriageContext context, RiskLevel normalized, List<RiskGap> unresolvedRiskGaps, List<RiskGap> confirmedRiskGaps) {
        Map<RiskSignalType, RiskGap> result = new LinkedHashMap<>();
        if (context.getRiskSignalState() != null) for (RiskSignalUnderstanding signal : context.getRiskSignalState()) {
            if (signal == null || signal.getType() == null || signal.getAssertion() == AssertionStatus.ABSENT) continue;
            if (containsSignal(confirmedRiskGaps, signal.getType()) || signal.getAssertion() == AssertionStatus.PRESENT) continue;
            result.put(signal.getType(), RiskGap.builder().slot(mapSlotCode(signal.getType())).relatedSignalType(signal.getType()).signalStatus(RiskSignalStatus.SUSPECTED).priority(85).reason("已观察到风险信号，建议继续确认。").build());
        }
        for (RiskGap unresolvedRiskGap : unresolvedRiskGaps) if (unresolvedRiskGap != null && unresolvedRiskGap.getRelatedSignalType() != null) result.putIfAbsent(unresolvedRiskGap.getRelatedSignalType(), RiskGap.builder().slot(unresolvedRiskGap.getSlot()).relatedSignalType(unresolvedRiskGap.getRelatedSignalType()).signalStatus(RiskSignalStatus.SUSPECTED).priority(90).reason("存在待确认风险问题，当前按疑似风险处理。").build());
        return new ArrayList<>(result.values());
    }

    private RiskDecisionType decideRiskAction(RiskLevel normalized, List<RiskGap> confirmedRiskGaps, List<RiskGap> suspectedRiskGaps, List<RiskGap> unresolvedRiskGaps) { if (Boolean.TRUE.equals(normalized.getShouldInterrupt()) && !confirmedRiskGaps.isEmpty()) return RiskDecisionType.TRIGGER_WARNING; if (!unresolvedRiskGaps.isEmpty() || Boolean.TRUE.equals(normalized.getNeedsMoreInfo())) return RiskDecisionType.ASK_RISK_CLARIFICATION; if (Boolean.TRUE.equals(normalized.getShouldInterrupt())) return RiskDecisionType.TRIGGER_WARNING; if (!suspectedRiskGaps.isEmpty() || normalized.getLevel() != null && normalized.getLevel() >= 2) return RiskDecisionType.MONITOR; return RiskDecisionType.NO_RISK_SIGNAL; }
    private boolean containsSignal(List<RiskGap> riskGaps, RiskSignalType signalType) { return riskGaps != null && signalType != null && riskGaps.stream().anyMatch(gap -> gap != null && gap.getRelatedSignalType() == signalType); }
    private boolean hasRecentUnresolvedHistory(TriageContext context, int threshold) { return context != null && context.getRiskDecisionHistory() != null && threshold > 0 && context.getRiskDecisionHistory().stream().filter(each -> each != null && each.getUnresolvedRiskGaps() != null && !each.getUnresolvedRiskGaps().isEmpty()).count() >= threshold; }
    private boolean hasRecentSuspectedHistory(TriageContext context, int threshold) { return context != null && context.getRiskDecisionHistory() != null && threshold > 0 && context.getRiskDecisionHistory().stream().filter(each -> each != null && each.getSuspectedRiskGaps() != null && !each.getSuspectedRiskGaps().isEmpty()).count() >= threshold; }
    private boolean hasRecentConfirmedHistory(TriageContext context, int threshold) { return context != null && context.getRiskDecisionHistory() != null && threshold > 0 && context.getRiskDecisionHistory().stream().filter(each -> each != null && each.getConfirmedRiskGaps() != null && !each.getConfirmedRiskGaps().isEmpty()).count() >= threshold; }
    private RiskSignalType mapSignalHint(String riskHint) { if (riskHint == null || riskHint.isBlank()) return null; return switch (riskHint) { case "BLEEDING" -> RiskSignalType.BLEEDING; case "DYSPNEA" -> RiskSignalType.DYSPNEA; case "SEIZURE" -> RiskSignalType.SEIZURE; case "CONSCIOUSNESS" -> RiskSignalType.ALTERED_CONSCIOUSNESS; case "PREGNANCY_BLEEDING" -> RiskSignalType.PREGNANCY_RELATED_BLEEDING; case "CHEST_PAIN", "CHEST_PAIN_WITH_DYSPNEA" -> RiskSignalType.CHEST_PAIN; default -> null; }; }
    private RiskSignalType mapSignalType(SlotCode slotCode) { if (slotCode == null) return null; return switch (slotCode) { case DYSPNEA_PRESENCE -> RiskSignalType.DYSPNEA; case BLEEDING_PRESENCE -> RiskSignalType.BLEEDING; case PREGNANCY_STATUS -> RiskSignalType.PREGNANCY_RELATED_BLEEDING; case SEIZURE_PRESENCE -> RiskSignalType.SEIZURE; case PRIMARY_SYMPTOM -> RiskSignalType.ALTERED_CONSCIOUSNESS; default -> null; }; }
    private SlotCode mapSlotCode(RiskSignalType signalType) { if (signalType == null) return null; return switch (signalType) { case DYSPNEA -> SlotCode.DYSPNEA_PRESENCE; case BLEEDING -> SlotCode.BLEEDING_PRESENCE; case PREGNANCY_RELATED_BLEEDING -> SlotCode.PREGNANCY_STATUS; case SEIZURE -> SlotCode.SEIZURE_PRESENCE; case ALTERED_CONSCIOUSNESS, CHEST_PAIN, UNKNOWN -> SlotCode.PRIMARY_SYMPTOM; }; }
    private String buildSystemPrompt() { return "你是风险分级 Worker，只输出 RiskLevel JSON。"; }
    private String buildUserPrompt(TriageContext context) { return "用户输入：" + StrUtil.blankToDefault(context.getLatestUserTurn(), "") + "\n已知风险信号：" + toJsonSafely(context.getRiskSignalState()) + "\n槽位状态：" + toJsonSafely(context.getSlotState()); }
}
