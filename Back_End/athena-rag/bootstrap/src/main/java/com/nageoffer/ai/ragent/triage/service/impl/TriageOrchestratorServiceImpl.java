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
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageReportData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageWarningData;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.repository.TriageRepository;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import com.nageoffer.ai.ragent.triage.service.TriageSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thin orchestrator that prepares context, delegates workflow execution to the FSM,
 * and adapts the final context into the response contract.
 */
@Service
@RequiredArgsConstructor
public class TriageOrchestratorServiceImpl implements TriageOrchestratorService {

    private final TriageStateMachine triageStateMachine;
    private final TriageSessionManager triageSessionManager;
    private final TriageRepository triageRepository;

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
            context = TriageContext.builder()
                    .sessionId(sessionId)
                    .build();
            context.ensureCollections();
            context.appendState("Session initialized.");
        } else {
            context.ensureCollections();
            context.appendState("Session restored from session manager.");
        }

        context.resetTurnState();
        context.appendConversation(latestUserInput);
        context.setUserInput(context.buildConversationTranscript());
        return context;
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
                context.setFinalReply("Please provide more details so the triage process can continue safely.");
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
