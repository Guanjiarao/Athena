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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.RiskDecisionType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskStratifierWorkerUnresolvedSignalTest {

    @Test
    void shouldBuildUnresolvedRiskDecisionFromUncertainDyspneaSignal() {
        RiskStratifierWorker worker = new RiskStratifierWorker(new StaticRiskLlmService(), new ObjectMapper(), new RiskHeuristicHelper());
        TriageContext context = TriageContext.builder().sessionId("TRIAGE-STEP6-UNRESOLVED-001").build();
        context.ensureCollections();
        context.getRiskSignalState().add(RiskSignalUnderstanding.builder()
                .type(RiskSignalType.DYSPNEA)
                .assertion(AssertionStatus.SUSPECTED)
                .evidence("说不清是不是喘不上气")
                .build());

        worker.execute(context);

        assertNotNull(context.getRiskDecision());
        assertEquals(RiskDecisionType.ASK_RISK_CLARIFICATION, context.getRiskDecision().getDecisionType());
        assertTrue(Boolean.TRUE.equals(context.getRiskDecision().getNeedsMoreInfo()));
        assertFalse(context.getRiskDecision().getUnresolvedRiskGaps().isEmpty());
        assertTrue(context.getRiskDecision().getUnresolvedRiskGaps().stream()
                .anyMatch(gap -> gap.getSlot() != null && gap.getSlot().name().equals("DYSPNEA_PRESENCE")));
    }

    private static final class StaticRiskLlmService implements LLMService {
        @Override
        public String chat(ChatRequest request) {
            List<ChatMessage> messages = request == null || request.getMessages() == null ? List.of() : request.getMessages();
            String prompt = messages.stream()
                    .filter(each -> each != null && each.getContent() != null)
                    .map(ChatMessage::getContent)
                    .reduce("", (left, right) -> left + "\n" + right);
            if (prompt.contains("风险分级 Worker")) {
                return "{" +
                        "\"level\":2," +
                        "\"score\":55," +
                        "\"evidence\":\"stubbed unresolved risk\"," +
                        "\"rationale\":\"stubbed unresolved\"," +
                        "\"shouldInterrupt\":false," +
                        "\"needsMoreInfo\":true," +
                        "\"missingCriticalSlots\":[]," +
                        "\"riskHints\":[]" +
                        "}";
            }
            return "{}";
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            if (callback != null) {
                callback.onComplete();
            }
            return () -> {
            };
        }
    }
}
