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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageReportData;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageWarningData;
import com.nageoffer.ai.ragent.triage.engine.TriageEvent;
import com.nageoffer.ai.ragent.triage.engine.TriageState;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.repository.TriageRepository;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.service.TriageSessionManager;
import com.nageoffer.ai.ragent.triage.worker.RiskStratifierWorker;
import com.nageoffer.ai.ragent.triage.worker.SOPValidatorWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticParserWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageOrchestratorServiceImplTest {

    @Mock
    private SemanticParserWorker semanticParserWorker;

    @Mock
    private SOPValidatorWorker sopValidatorWorker;

    @Mock
    private RiskStratifierWorker riskStratifierWorker;

    @Mock
    private TriageModelGateway triageModelGateway;

    @Mock
    private TriageRepository triageRepository;

    private TriageSessionManager triageSessionManager;
    private TriageOrchestratorServiceImpl orchestratorService;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TriageSessionProperties sessionProperties = new TriageSessionProperties();
        sessionProperties.setContextWindowMaxChars(40);
        sessionProperties.setTargetRecentWindowChars(20);
        sessionProperties.setSummaryMaxChars(120);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        triageSessionManager = new TriageSessionManager(redisTemplate, objectMapper, sessionProperties);
        TriageStateMachine triageStateMachine = new TriageStateMachine(
                semanticParserWorker,
                sopValidatorWorker,
                riskStratifierWorker,
                triageModelGateway
        );
        orchestratorService = new TriageOrchestratorServiceImpl(
                triageStateMachine,
                triageSessionManager,
                triageRepository,
                triageModelGateway,
                sessionProperties
        );
    }

    @Test
    void shouldReturnReportAndPersistCompletedSession() {
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setExtractedSymptoms(Collections.singletonList(symptom("abdominal pain", "right lower abdomen")));
            return context;
        }).when(semanticParserWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setMissingFields(Collections.emptyList());
            return context;
        }).when(sopValidatorWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setRiskAssessment(RiskLevel.builder()
                    .level(2)
                    .score(42D)
                    .evidence("No red flag found during assessment.")
                    .rationale("Low-risk branch.")
                    .build());
            return context;
        }).when(riskStratifierWorker).execute(any(TriageContext.class));
        when(triageModelGateway.chatWithReportModel(anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn("Structured report");

        TriageAnalyzeResponse response = orchestratorService.analyze(TriageAnalyzeRequest.builder()
                .userInput("abdominal pain for one day")
                .build());

        assertEquals("GENERATE_REPORT", response.getAction());
        assertEquals(2, response.getRiskLevel());
        assertInstanceOf(TriageReportData.class, response.getData());

        TriageReportData data = (TriageReportData) response.getData();
        TriageContext cachedContext = triageSessionManager.getContext(data.getSessionId());
        assertNotNull(cachedContext);
        assertEquals(TriageState.COMPLETED, cachedContext.getCurrentState());
        assertFalse(cachedContext.getAuditTrail().isEmpty());
        assertTrue(cachedContext.getAuditTrail().stream().anyMatch(each -> each.getTriggerEvent() == TriageEvent.REPORT_READY));
        assertEquals("Structured report", data.getReport());
        verify(triageRepository).save(any(TriageContext.class));
    }

    @Test
    void shouldInterruptSessionWhenInformationIsMissing() {
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setExtractedSymptoms(Collections.singletonList(symptom("abdominal pain", null)));
            return context;
        }).when(semanticParserWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setMissingFields(Arrays.asList("pain location", "fever status"));
            return context;
        }).when(sopValidatorWorker).execute(any(TriageContext.class));

        TriageAnalyzeResponse response = orchestratorService.analyze(TriageAnalyzeRequest.builder()
                .sessionId("session-clarification")
                .userInput("and I also feel nausea")
                .build());

        assertEquals("ASK_CLARIFICATION", response.getAction());
        assertEquals(0, response.getRiskLevel());
        assertInstanceOf(TriageClarificationData.class, response.getData());

        TriageClarificationData data = (TriageClarificationData) response.getData();
        assertEquals(2, data.getMissingFields().size());
        assertFalse(data.getFollowUpQuestion().isBlank());
        verify(riskStratifierWorker, never()).execute(any(TriageContext.class));
        verifyNoInteractions(triageModelGateway);
        verify(triageRepository).save(any(TriageContext.class));
    }

    @Test
    void shouldReturnWarningAndPersistInterruptedSessionForHighRisk() {
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setExtractedSymptoms(Collections.singletonList(symptom("chest pain", "chest")));
            return context;
        }).when(semanticParserWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setMissingFields(Collections.emptyList());
            return context;
        }).when(sopValidatorWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setRiskAssessment(RiskLevel.builder()
                    .level(4)
                    .score(95D)
                    .evidence("Chest pain with dyspnea is a high-risk combination.")
                    .rationale("Emergency branch.")
                    .build());
            return context;
        }).when(riskStratifierWorker).execute(any(TriageContext.class));

        TriageAnalyzeResponse response = orchestratorService.analyze(TriageAnalyzeRequest.builder()
                .sessionId("session-warning")
                .userInput("I have chest pain and shortness of breath")
                .build());

        assertEquals("TRIGGER_WARNING", response.getAction());
        assertEquals(4, response.getRiskLevel());
        assertInstanceOf(TriageWarningData.class, response.getData());

        TriageWarningData data = (TriageWarningData) response.getData();
        assertFalse(data.getWarningText().isBlank());
        verifyNoInteractions(triageModelGateway);
        verify(triageRepository).save(any(TriageContext.class));
    }

    @Test
    void shouldCompactOldConversationIntoSummaryByCharBudget() {
        doAnswer(invocation -> invocation.getArgument(0)).when(semanticParserWorker).execute(any(TriageContext.class));
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setMissingFields(Collections.singletonList("持续时间"));
            return context;
        }).when(sopValidatorWorker).execute(any(TriageContext.class));
        when(triageModelGateway.summarizeConversationMemory(anyList(), anyInt())).thenReturn("早期摘要：腹痛伴恶心，待补充持续时间。");

        String sessionId = "session-summary";
        orchestratorService.analyze(TriageAnalyzeRequest.builder().sessionId(sessionId).userInput("右下腹疼痛明显").build());
        orchestratorService.analyze(TriageAnalyzeRequest.builder().sessionId(sessionId).userInput("还有一点恶心").build());
        orchestratorService.analyze(TriageAnalyzeRequest.builder().sessionId(sessionId).userInput("按压时更疼").build());
        orchestratorService.analyze(TriageAnalyzeRequest.builder().sessionId(sessionId).userInput("今天下午更明显").build());

        TriageContext cachedContext = triageSessionManager.getContext(sessionId);
        assertNotNull(cachedContext);
        assertTrue(cachedContext.recentConversationChars() <= 20);
        assertTrue(cachedContext.getConversationSummary().contains("早期摘要"));
        assertTrue(cachedContext.getUserInput().contains("【历史摘要】"));
        assertTrue(cachedContext.getUserInput().contains("【最近对话】"));
        verify(triageModelGateway).summarizeConversationMemory(anyList(), anyInt());
    }

    private Symptom symptom(String name, String bodyPart) {
        return Symptom.builder()
                .name(name)
                .bodyPart(bodyPart)
                .build();
    }
}
