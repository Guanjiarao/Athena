

package com.nageoffer.ai.ragent.triage.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.config.TriageAiProperties;
import com.nageoffer.ai.ragent.triage.slot.AnsweredSlotFlowCollector;
import com.nageoffer.ai.ragent.triage.slot.AnsweredSlotSemanticsAssembler;
import com.nageoffer.ai.ragent.triage.slot.CorrectionPhraseParser;
import com.nageoffer.ai.ragent.triage.slot.CorrectionTargetResolver;
import com.nageoffer.ai.ragent.triage.slot.SlotAnswerInferenceHelper;

public class TurnUnderstandingExecutionChainFactory {

    public TurnUnderstandingExecutionEngine create(LLMService llmService,
                                                   ObjectMapper objectMapper,
                                                   ComplaintFallbackResolver complaintFallbackResolver,
                                                   SlotAnswerInferenceHelper slotAnswerInferenceHelper,
                                                   TriageAiProperties triageAiProperties) {
        AnsweredSlotFlowCollector answeredSlotFlowCollector = new AnsweredSlotFlowCollector(slotAnswerInferenceHelper);
        AnsweredSlotSemanticsAssembler answeredSlotSemanticsAssembler = new AnsweredSlotSemanticsAssembler(
                answeredSlotFlowCollector,
                new CorrectionPhraseParser(),
                new CorrectionTargetResolver(slotAnswerInferenceHelper));
        TurnComplaintSemanticsCoordinator turnComplaintSemanticsCoordinator = new TurnComplaintSemanticsCoordinator(
                complaintFallbackResolver,
                answeredSlotFlowCollector,
                answeredSlotSemanticsAssembler);
        TurnUnderstandingPostProcessor turnUnderstandingPostProcessor = new TurnUnderstandingPostProcessor(turnComplaintSemanticsCoordinator);
        TurnUnderstandingResultHandoff turnUnderstandingResultHandoff = new TurnUnderstandingResultHandoff(turnUnderstandingPostProcessor);
        TurnUnderstandingExecutionShell turnUnderstandingExecutionShell = new TurnUnderstandingExecutionShell(turnUnderstandingResultHandoff);
        return new TurnUnderstandingExecutionEngine(llmService, objectMapper, turnUnderstandingExecutionShell, triageAiProperties);
    }
}
