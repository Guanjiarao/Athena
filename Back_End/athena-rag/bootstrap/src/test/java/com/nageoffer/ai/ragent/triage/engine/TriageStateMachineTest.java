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

import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.worker.RiskStratifierWorker;
import com.nageoffer.ai.ragent.triage.worker.SOPValidatorWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticParserWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageStateMachineTest {

    @Mock
    private SemanticParserWorker semanticParserWorker;

    @Mock
    private SOPValidatorWorker sopValidatorWorker;

    @Mock
    private RiskStratifierWorker riskStratifierWorker;

    @Mock
    private TriageModelGateway triageModelGateway;

    private TriageStateMachine triageStateMachine;

    @BeforeEach
    void setUp() {
        triageStateMachine = new TriageStateMachine(
                semanticParserWorker,
                sopValidatorWorker,
                riskStratifierWorker,
                triageModelGateway
        );
    }

    @Test
    void shouldCompleteNormalStateFlow() {
        doAnswer(invocation -> {
            TriageContext context = invocation.getArgument(0);
            context.setExtractedSymptoms(Collections.singletonList(symptom("abdominal pain", "abdomen")));
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
                    .level(1)
                    .score(18D)
                    .evidence("No emergency red flag.")
                    .rationale("Low risk path.")
                    .build());
            return context;
        }).when(riskStratifierWorker).execute(any(TriageContext.class));
        when(triageModelGateway.chatWithReportModel(anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn("Generated report");

        TriageContext context = TriageContext.builder()
                .sessionId("fsm-normal")
                .userInput("abdominal pain for one day")
                .build();

        TriageState finalState = triageStateMachine.execute(context);

        assertEquals(TriageState.COMPLETED, finalState);
        assertEquals(TriageAction.GENERATE_REPORT, context.getNextAction());
        assertEquals("Generated report", context.getFinalReply());
        verify(triageModelGateway).chatWithReportModel(anyList(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void shouldInterruptWhenValidationFindsMissingFields() {
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

        TriageContext context = TriageContext.builder()
                .sessionId("fsm-missing")
                .userInput("abdominal pain")
                .build();

        TriageState finalState = triageStateMachine.execute(context);

        assertEquals(TriageState.INTERRUPTED, finalState);
        assertEquals(TriageAction.ASK_CLARIFICATION, context.getNextAction());
        assertFalse(context.getFinalReply().isBlank());
        verifyNoInteractions(triageModelGateway);
    }

    @Test
    void shouldInterruptWhenRiskAssessmentFindsHighRisk() {
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
                    .score(96D)
                    .evidence("Chest pain with shortness of breath.")
                    .rationale("High risk path.")
                    .build());
            return context;
        }).when(riskStratifierWorker).execute(any(TriageContext.class));

        TriageContext context = TriageContext.builder()
                .sessionId("fsm-warning")
                .userInput("chest pain and shortness of breath")
                .build();

        TriageState finalState = triageStateMachine.execute(context);

        assertEquals(TriageState.INTERRUPTED, finalState);
        assertEquals(TriageAction.TRIGGER_WARNING, context.getNextAction());
        assertFalse(context.getFinalReply().isBlank());
        verify(triageModelGateway, never()).chatWithReportModel(anyList(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void shouldResolveTransitionsExplicitly() {
        assertEquals(TriageState.PARSING, triageStateMachine.nextState(TriageState.INIT, TriageEvent.START_ANALYSIS));
        assertEquals(TriageState.VALIDATING, triageStateMachine.nextState(TriageState.PARSING, TriageEvent.PARSE_SUCCESS));
    }

    private Symptom symptom(String name, String bodyPart) {
        return Symptom.builder()
                .name(name)
                .bodyPart(bodyPart)
                .build();
    }
}
