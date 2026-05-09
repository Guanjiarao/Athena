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

package com.nageoffer.ai.ragent.triage.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.triage.engine.TriageStateMachine;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.worker.ComplaintFallbackResolver;
import com.nageoffer.ai.ragent.triage.worker.FactExtractor;
import com.nageoffer.ai.ragent.triage.worker.FactHeuristicExtractor;
import com.nageoffer.ai.ragent.triage.worker.QuestionPlanSupport;
import com.nageoffer.ai.ragent.triage.worker.QuestionPlanner;
import com.nageoffer.ai.ragent.triage.worker.RiskHeuristicHelper;
import com.nageoffer.ai.ragent.triage.worker.RiskStratifierWorker;
import com.nageoffer.ai.ragent.triage.worker.SOPValidatorWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticParserWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticSymptomHeuristicHelper;
import com.nageoffer.ai.ragent.triage.worker.SlotManager;
import com.nageoffer.ai.ragent.triage.worker.SlotStateSupport;
import com.nageoffer.ai.ragent.triage.worker.StateReducer;
import com.nageoffer.ai.ragent.triage.worker.TurnUnderstandingWorker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TriageEvalRealExecutor {

    private final ObjectMapper objectMapper;
    private final TriageStateMachine triageStateMachine;

    public TriageEvalRealExecutor(LLMService llmService, TriageModelGateway triageModelGateway) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        RiskHeuristicHelper riskHeuristicHelper = new RiskHeuristicHelper();
        TurnUnderstandingWorker turnUnderstandingWorker = new TurnUnderstandingWorker(llmService, objectMapper);
        ComplaintFallbackResolver complaintFallbackResolver = new ComplaintFallbackResolver();
        SemanticParserWorker semanticParserWorker = new SemanticParserWorker(
                llmService,
                objectMapper,
                new SemanticSymptomHeuristicHelper(complaintFallbackResolver)
        );
        FactExtractor factExtractor = new FactExtractor(
                llmService,
                objectMapper,
                new FactHeuristicExtractor(complaintFallbackResolver)
        );
        StateReducer stateReducer = new StateReducer();
        SlotManager slotManager = new SlotManager(new SlotStateSupport());
        QuestionPlanner questionPlanner = new QuestionPlanner(new QuestionPlanSupport());
        SOPValidatorWorker sopValidatorWorker = new SOPValidatorWorker(llmService, objectMapper);
        RiskStratifierWorker riskStratifierWorker = new RiskStratifierWorker(
                llmService,
                objectMapper,
                riskHeuristicHelper
        );
        this.triageStateMachine = new TriageStateMachine(
                turnUnderstandingWorker,
                semanticParserWorker,
                factExtractor,
                stateReducer,
                slotManager,
                questionPlanner,
                sopValidatorWorker,
                riskStratifierWorker,
                triageModelGateway,
                riskHeuristicHelper
        );
    }

    public TriageContext execute(TriageEvalCase testCase) {
        TriageContext context = TriageContext.builder()
                .sessionId(testCase == null ? "eval-session" : testCase.getId())
                .build();
        context.ensureCollections();
        applyEvalContext(context, testCase == null ? null : testCase.getContext());
        if (testCase != null && testCase.getTurns() != null) {
            for (int i = 0; i < testCase.getTurns().size(); i++) {
                TriageEvalCase.Turn turn = testCase.getTurns().get(i);
                if (turn == null || turn.getText() == null || turn.getText().isBlank()) {
                    continue;
                }
                context.resetTurnState();
                applyPerTurnContext(context, testCase.getContext(), i);
                context.setLatestUserTurn(turn.getText().trim());
                context.appendConversation(turn.getText().trim());
                context.setUserInput(context.buildConversationTranscript(true));
                triageStateMachine.execute(context);
            }
        }
        return context;
    }

    private void applyPerTurnContext(TriageContext context, TriageEvalCase.EvalContext evalContext, int turnIndex) {
        if (evalContext == null || evalContext.getPerTurnContext() == null || turnIndex < 0 || turnIndex >= evalContext.getPerTurnContext().size()) {
            return;
        }
        Map<String, Object> raw = evalContext.getPerTurnContext().get(turnIndex);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        TriageEvalCase.EvalContext perTurnContext = objectMapper.convertValue(raw, TriageEvalCase.EvalContext.class);
        applyEvalContext(context, perTurnContext);
    }

    private void applyEvalContext(TriageContext context, TriageEvalCase.EvalContext evalContext) {
        if (context == null || evalContext == null) {
            return;
        }
        context.ensureCollections();
        if (evalContext.getLastAskedSlots() != null && !evalContext.getLastAskedSlots().isEmpty()) {
            context.setLastAskedSlots(toSlotCodes(evalContext.getLastAskedSlots()));
        }
        if (evalContext.getPendingSlots() != null && !evalContext.getPendingSlots().isEmpty()) {
            context.setPendingSlots(toSlotCodes(evalContext.getPendingSlots()));
        }
        if (evalContext.getSlotState() != null && !evalContext.getSlotState().isEmpty()) {
            SlotState slotState = context.getSlotState();
            slotState.ensureInitialized();
            for (Map.Entry<String, TriageEvalCase.SlotSeed> entry : evalContext.getSlotState().entrySet()) {
                SlotCode slotCode = toSlotCode(entry.getKey());
                TriageEvalCase.SlotSeed slotSeed = entry.getValue();
                if (slotCode == null || slotSeed == null) {
                    continue;
                }
                slotState.put(SlotValue.builder()
                        .slot(slotCode)
                        .value(slotSeed.getValue())
                        .status(toSlotStatus(slotSeed.getStatus()))
                        .evidence("seeded from case context")
                        .updatedAt(Instant.now())
                        .build());
            }
            context.setSlotState(slotState);
        }
    }

    private List<SlotCode> toSlotCodes(List<String> rawSlotCodes) {
        List<SlotCode> result = new ArrayList<>();
        if (rawSlotCodes == null) {
            return result;
        }
        for (String rawSlotCode : rawSlotCodes) {
            SlotCode slotCode = toSlotCode(rawSlotCode);
            if (slotCode != null) {
                result.add(slotCode);
            }
        }
        return result;
    }

    private SlotCode toSlotCode(String rawSlotCode) {
        if (rawSlotCode == null || rawSlotCode.isBlank()) {
            return null;
        }
        try {
            return SlotCode.valueOf(rawSlotCode.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private SlotStatus toSlotStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return SlotStatus.FILLED;
        }
        try {
            return SlotStatus.valueOf(rawStatus.trim());
        } catch (IllegalArgumentException ex) {
            return SlotStatus.FILLED;
        }
    }

    public static TriageModelGateway stubGateway() {
        return new TriageModelGateway() {
            @Override
            public String chatWithTextModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
                return "";
            }

            @Override
            public String chatWithReportModel(List<ChatMessage> messages, Double temperature, Double topP, Integer maxTokens) {
                return "";
            }

            @Override
            public String summarizeConversationMemory(List<ChatMessage> messages, Integer maxTokens) {
                return "";
            }

            @Override
            public String resolveVisionModel(String requestModel) {
                return requestModel == null ? "stub-vision-model" : requestModel;
            }
        };
    }

    public static LLMService heuristicLlmStub() {
        return new LLMService() {
            @Override
            public String chat(ChatRequest request) {
                List<ChatMessage> messages = request == null || request.getMessages() == null
                        ? List.of()
                        : request.getMessages();
                String prompt = messages.stream()
                        .filter(each -> each != null && each.getContent() != null)
                        .map(ChatMessage::getContent)
                        .reduce("", (left, right) -> left + "\n" + right);
                if (prompt.contains("风险分级 Worker")) {
                    return """
                            {"level":2,"score":55,"evidence":"stubbed risk assessment","rationale":"stubbed","shouldInterrupt":false,"needsMoreInfo":false,"missingCriticalSlots":[],"riskHints":[]}
                            """;
                }
                if (prompt.contains("回合语义理解")) {
                    return """
                            {"intent":"UNKNOWN","answeredSlots":[],"riskSignals":[],"corrections":[],"notes":[],"confidence":0.0}
                            """;
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
        };
    }
}
