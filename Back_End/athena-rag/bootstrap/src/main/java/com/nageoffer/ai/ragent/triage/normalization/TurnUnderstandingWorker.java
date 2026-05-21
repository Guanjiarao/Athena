

package com.nageoffer.ai.ragent.triage.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.config.TriageAiProperties;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.slot.SlotAnswerInferenceHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TurnUnderstandingWorker {
    private final TurnUnderstandingExecutionEngine turnUnderstandingExecutionEngine;

    @Autowired
    public TurnUnderstandingWorker(LLMService llmService, ObjectMapper objectMapper, TriageAiProperties triageAiProperties) {
        this(llmService, objectMapper, new ComplaintFallbackResolver(), new TurnUnderstandingExecutionChainFactory(), triageAiProperties);
    }

    TurnUnderstandingWorker(LLMService llmService,
                            ObjectMapper objectMapper,
                            ComplaintFallbackResolver complaintFallbackResolver) {
        this(llmService, objectMapper, complaintFallbackResolver, new TurnUnderstandingExecutionChainFactory(), null);
    }

    TurnUnderstandingWorker(LLMService llmService,
                            ObjectMapper objectMapper,
                            ComplaintFallbackResolver complaintFallbackResolver,
                            TurnUnderstandingExecutionChainFactory executionChainFactory,
                            TriageAiProperties triageAiProperties) {
        this.turnUnderstandingExecutionEngine = executionChainFactory.create(
                llmService,
                objectMapper,
                complaintFallbackResolver,
                buildSlotAnswerInferenceHelper(complaintFallbackResolver),
                triageAiProperties);
    }

    public TriageContext execute(TriageContext context) {
        return turnUnderstandingExecutionEngine.execute(context);
    }

    private static SlotAnswerInferenceHelper buildSlotAnswerInferenceHelper(ComplaintFallbackResolver complaintFallbackResolver) { return new SlotAnswerInferenceHelper(complaintFallbackResolver); }
}
