

package com.nageoffer.ai.ragent.triage.normalization;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.SemanticParseResult;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.worker.AbstractStructuredTriageWorker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticParserWorker extends AbstractStructuredTriageWorker {

    private final SemanticSymptomHeuristicHelper semanticSymptomHeuristicHelper;

    public SemanticParserWorker(LLMService llmService,
                                ObjectMapper objectMapper,
                                SemanticSymptomHeuristicHelper semanticSymptomHeuristicHelper,
                                com.nageoffer.ai.ragent.triage.config.TriageAiProperties triageAiProperties) {
        super(llmService, objectMapper, triageAiProperties);
        this.semanticSymptomHeuristicHelper = semanticSymptomHeuristicHelper;
    }

    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();
        if (StrUtil.isBlank(context.getUserInput())) {
            context.setExtractedSymptoms(new ArrayList<>());
            return context;
        }

        List<Symptom> llmSymptoms = new ArrayList<>();
        try {
            String rawResponse = invokeLlm(resolveModelId("semanticParserModel", "deepseek-v4-flash"), buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D);
            llmSymptoms = parseSemanticResponse(rawResponse);
        } catch (Exception ignored) {
            llmSymptoms = new ArrayList<>();
        }

        List<Symptom> heuristicSymptoms = semanticSymptomHeuristicHelper.heuristicExtract(context.getUserInput());
        List<Symptom> mergedSymptoms = semanticSymptomHeuristicHelper.mergeSymptoms(llmSymptoms, heuristicSymptoms);
        context.setExtractedSymptoms(semanticSymptomHeuristicHelper.filterNegatedSymptoms(mergedSymptoms, context.getUserInput()));
        return context;
    }

    private String buildSystemPrompt() {
        return SemanticParserPromptTemplates.systemPrompt();
    }

    private String buildUserPrompt(TriageContext context) {
        return SemanticParserPromptTemplates.userPrompt(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                context.getUserInput()
        );
    }

    private List<Symptom> parseSemanticResponse(String rawResponse) {
        String payload = extractJsonPayload(rawResponse);
        if (StrUtil.isBlank(payload)) {
            return new ArrayList<>();
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("[")) {
            List<Symptom> symptoms = readTypeSafely(
                    rawResponse,
                    new TypeReference<List<Symptom>>() {
                    },
                    new ArrayList<>(),
                    "语义解析-数组兼容"
            );
            return semanticSymptomHeuristicHelper.normalizeSymptoms(symptoms);
        }
        SemanticParseResult result = readObjectSafely(
                rawResponse,
                SemanticParseResult.class,
                SemanticParseResult.builder().build(),
                "语义解析"
        );
        return semanticSymptomHeuristicHelper.normalizeSymptoms(result.getExtractedSymptoms());
    }
}
