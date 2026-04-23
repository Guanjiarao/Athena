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

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageReportData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageWarningData;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.repository.TriageRepository;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import com.nageoffer.ai.ragent.triage.service.TriageSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TriageOrchestratorServiceImpl implements TriageOrchestratorService {

    private final TriageStateMachine triageStateMachine;
    private final TriageSessionManager triageSessionManager;
    private final TriageRepository triageRepository;
    private final TriageModelGateway triageModelGateway;
    private final TriageSessionProperties triageSessionProperties;

    @Override
    public TriageAnalyzeResponse analyze(TriageAnalyzeRequest request) {
        validateRequest(request);
        TriageContext context = loadOrCreateContext(request);
        try {
            triageStateMachine.execute(context);
            return toResponse(context);
        } finally {
            triageSessionManager.saveContext(context);
            persistTerminalContext(context);
        }
    }

    private TriageContext loadOrCreateContext(TriageAnalyzeRequest request) {
        String sessionId = StrUtil.blankToDefault(request.getSessionId(), IdUtil.getSnowflakeNextIdStr());
        TriageContext context = triageSessionManager.getContext(sessionId);
        String latestUserInput = request.getUserInput().trim();
        if (context == null) {
            context = TriageContext.builder().sessionId(sessionId).build();
            context.ensureCollections();
            context.appendState("Session initialized.");
        } else {
            context.ensureCollections();
            context.appendState("Session restored from session manager.");
        }
        context.resetTurnState();
        context.appendConversation(latestUserInput);
        compressConversationMemoryIfNeeded(context);
        context.setUserInput(context.buildConversationTranscript(true));
        return context;
    }

    private void compressConversationMemoryIfNeeded(TriageContext context) {
        int contextWindowMaxChars = safePositive(triageSessionProperties.getContextWindowMaxChars(), 2400);
        int targetRecentWindowChars = safePositive(triageSessionProperties.getTargetRecentWindowChars(), 1200);
        int summaryMaxChars = safePositive(triageSessionProperties.getSummaryMaxChars(), 400);
        int beforeTotalChars = context.totalTranscriptChars(true);
        int beforeRecentChars = context.recentConversationChars();
        int beforeSummaryChars = StrUtil.length(context.getConversationSummary());
        context.appendState("Memory window check: totalChars=" + beforeTotalChars
                + ", recentChars=" + beforeRecentChars
                + ", summaryChars=" + beforeSummaryChars
                + ", maxChars=" + contextWindowMaxChars
                + ", targetRecentChars=" + targetRecentWindowChars);
        if (beforeTotalChars <= contextWindowMaxChars) {
            context.appendState("Memory window within budget, skip compression.");
            return;
        }
        List<String> evictedTurns = context.evictOldestTurnsByCharBudget(targetRecentWindowChars);
        if (evictedTurns.isEmpty()) {
            context.appendState("Memory window exceeded but no turns could be evicted.");
            return;
        }
        String summary = summarizeConversation(context, evictedTurns, summaryMaxChars);
        if (StrUtil.isNotBlank(summary)) {
            context.setConversationSummary(summary);
            context.appendState("Memory compressed: evictedTurns=" + evictedTurns.size()
                    + ", recentChars=" + context.recentConversationChars()
                    + ", summaryChars=" + StrUtil.length(summary)
                    + ", totalChars=" + context.totalTranscriptChars(true));
            return;
        }
        context.appendState("Memory compression produced empty summary, keeping previous summary.");
    }

    private String summarizeConversation(TriageContext context, List<String> evictedTurns, int summaryMaxChars) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是医疗分诊会话的记忆压缩助手。请把更早的对话压缩成简洁中文摘要，只保留已确认的症状、部位、持续时间、严重程度、伴随症状、风险线索和仍待补充的信息。不要输出诊断，不要输出 JSON。"));
        messages.add(ChatMessage.user(buildSummaryPrompt(context, evictedTurns, summaryMaxChars)));
        try {
            String summary = triageModelGateway.summarizeConversationMemory(messages, 400);
            if (StrUtil.isNotBlank(summary)) {
                return truncate(summary.trim(), summaryMaxChars);
            }
        } catch (Exception ignored) {
        }
        return buildHeuristicSummary(context, evictedTurns, summaryMaxChars);
    }

    private String buildSummaryPrompt(TriageContext context, List<String> evictedTurns, int summaryMaxChars) {
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(context.getConversationSummary())) {
            builder.append("已有摘要:\n").append(context.getConversationSummary()).append("\n\n");
        }
        builder.append("需压缩的旧对话:\n").append(String.join("\n", evictedTurns)).append("\n\n");
        if (context.getExtractedSymptoms() != null && !context.getExtractedSymptoms().isEmpty()) {
            builder.append("已提取症状:\n");
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (symptom == null) {
                    continue;
                }
                builder.append("- ").append(StrUtil.blankToDefault(symptom.getName(), "症状"));
                if (StrUtil.isNotBlank(symptom.getBodyPart())) {
                    builder.append("，部位").append(symptom.getBodyPart());
                }
                if (StrUtil.isNotBlank(symptom.getDuration())) {
                    builder.append("，持续").append(symptom.getDuration());
                }
                if (StrUtil.isNotBlank(symptom.getSeverity())) {
                    builder.append("，程度").append(symptom.getSeverity());
                }
                builder.append("\n");
            }
        }
        if (context.getMissingFields() != null && !context.getMissingFields().isEmpty()) {
            builder.append("待补充字段:\n").append(String.join("、", context.getMissingFields())).append("\n");
        }
        builder.append("请输出不超过").append(summaryMaxChars).append("字的摘要。");
        return builder.toString();
    }

    private String buildHeuristicSummary(TriageContext context, List<String> evictedTurns, int summaryMaxChars) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(context.getConversationSummary())) {
            parts.add(context.getConversationSummary().trim());
        }
        if (context.getExtractedSymptoms() != null && !context.getExtractedSymptoms().isEmpty()) {
            List<String> symptomLines = new ArrayList<>();
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (symptom == null || StrUtil.isBlank(symptom.getName())) {
                    continue;
                }
                StringBuilder line = new StringBuilder(symptom.getName().trim());
                if (StrUtil.isNotBlank(symptom.getBodyPart())) {
                    line.append("(").append(symptom.getBodyPart().trim()).append(")");
                }
                if (StrUtil.isNotBlank(symptom.getDuration())) {
                    line.append(" 持续").append(symptom.getDuration().trim());
                }
                symptomLines.add(line.toString());
            }
            if (!symptomLines.isEmpty()) {
                parts.add("已确认症状：" + String.join("；", symptomLines));
            }
        }
        if (context.getMissingFields() != null && !context.getMissingFields().isEmpty()) {
            parts.add("待补充：" + String.join("、", context.getMissingFields()));
        }
        if (!evictedTurns.isEmpty()) {
            parts.add("早期原话：" + String.join("；", evictedTurns));
        }
        return truncate(String.join("。", parts), summaryMaxChars);
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String truncate(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxChars);
    }

    private void validateRequest(TriageAnalyzeRequest request) {
        if (request == null) {
            throw new ClientException("Triage request must not be null.");
        }
        if (StrUtil.isBlank(request.getUserInput())) {
            throw new ClientException("userInput must not be blank.");
        }
    }

    private void persistTerminalContext(TriageContext context) {
        if (context == null || context.getCurrentState() == null || !context.getCurrentState().isTerminal()) {
            return;
        }
        triageRepository.save(context);
    }

    private TriageAnalyzeResponse toResponse(TriageContext context) {
        TriageAction action = context.getNextAction();
        if (action == null) {
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            if (StrUtil.isBlank(context.getFinalReply())) {
                context.setFinalReply("为了继续判断，请再补充一些不适细节。");
            }
            return toClarificationResponse(context);
        }
        return switch (action) {
            case ASK_CLARIFICATION -> toClarificationResponse(context);
            case TRIGGER_WARNING -> toWarningResponse(context);
            case GENERATE_REPORT -> toReportResponse(context);
        };
    }

    private TriageAnalyzeResponse toClarificationResponse(TriageContext context) {
        TriageClarificationData data = TriageClarificationData.builder()
                .sessionId(context.getSessionId())
                .extractedSymptoms(context.getExtractedSymptoms())
                .missingFields(context.getMissingFields())
                .followUpQuestion(context.getFinalReply())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.ASK_CLARIFICATION.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(0)
                .build();
    }

    private TriageAnalyzeResponse toWarningResponse(TriageContext context) {
        TriageWarningData data = TriageWarningData.builder()
                .sessionId(context.getSessionId())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .warningText(context.getFinalReply())
                .emergencyGuidance("Go to an emergency department or offline clinic immediately if symptoms continue to worsen.")
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.TRIGGER_WARNING.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }

    private TriageAnalyzeResponse toReportResponse(TriageContext context) {
        TriageReportData data = TriageReportData.builder()
                .sessionId(context.getSessionId())
                .report(context.getFinalReply())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.GENERATE_REPORT.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }
}
