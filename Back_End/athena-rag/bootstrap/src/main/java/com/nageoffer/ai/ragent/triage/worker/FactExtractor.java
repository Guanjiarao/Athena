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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.FactType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.prompt.FactExtractorPromptTemplates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FactExtractor extends AbstractStructuredTriageWorker {

    private final FactHeuristicExtractor factHeuristicExtractor;

    public FactExtractor(LLMService llmService,
                         ObjectMapper objectMapper,
                         FactHeuristicExtractor factHeuristicExtractor) {
        super(llmService, objectMapper);
        this.factHeuristicExtractor = factHeuristicExtractor;
    }

    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();
        String latestTurn = StrUtil.blankToDefault(context.getLatestUserTurn(), "").trim();
        if (latestTurn.isEmpty()) {
            return context;
        }

        List<Fact> llmFacts = new ArrayList<>();
        try {
            String rawResponse = invokeLlm(buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D);
            llmFacts = parseFacts(rawResponse, context);
        } catch (Exception ignored) {
            llmFacts = new ArrayList<>();
        }

        List<Fact> mergedFacts = mergeFacts(llmFacts, factHeuristicExtractor.extract(latestTurn, context));
        context.appendFacts(mergedFacts);
        return context;
    }

    private String buildSystemPrompt() {
        return FactExtractorPromptTemplates.systemPrompt();
    }

    private String buildUserPrompt(TriageContext context) {
        return FactExtractorPromptTemplates.userPrompt(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                StrUtil.blankToDefault(context.getLatestUserTurn(), ""),
                toJsonSafely(context.getLastAskedSlots()),
                toJsonSafely(context.getPendingSlots()),
                StrUtil.blankToDefault(context.getConversationSummary(), "无")
        );
    }

    private List<Fact> parseFacts(String rawResponse, TriageContext context) {
        Map<String, Object> payload = readTypeSafely(rawResponse, new TypeReference<Map<String, Object>>() {}, new LinkedHashMap<>(), "事实抽取");
        Object factsObject = payload.get("facts");
        if (!(factsObject instanceof List<?> factsList) || factsList.isEmpty()) {
            return new ArrayList<>();
        }
        List<Fact> result = new ArrayList<>();
        int sourceTurnIndex = Math.max(0, context.getConversationHistory().size() - 1);
        for (Object item : factsList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            SlotCode slotCode = parseSlotCode(String.valueOf(map.get("slot")));
            String canonicalValue = trimToNull(String.valueOf(map.get("canonicalValue")));
            if (slotCode == null || canonicalValue == null) {
                continue;
            }
            result.add(Fact.builder()
                    .type(FactType.SLOT_EVIDENCE)
                    .slot(slotCode)
                    .canonicalValue(canonicalValue)
                    .polarity(parsePolarity(String.valueOf(map.get("polarity"))))
                    .confidence(parseConfidence(map.get("confidence")))
                    .evidence(trimToNull(String.valueOf(map.get("evidence"))))
                    .sourceTurnIndex(sourceTurnIndex)
                    .sourceText(context.getLatestUserTurn())
                    .build());
        }
        return result;
    }

    private List<Fact> mergeFacts(List<Fact> llmFacts, List<Fact> heuristicFacts) {
        LinkedHashMap<String, Fact> merged = new LinkedHashMap<>();
        for (Fact fact : llmFacts) {
            if (fact == null || fact.getSlot() == null || StrUtil.isBlank(fact.getCanonicalValue())) {
                continue;
            }
            merged.put(fact.getSlot().name() + "::" + fact.getCanonicalValue(), fact);
        }
        for (Fact fact : heuristicFacts) {
            if (fact == null || fact.getSlot() == null || StrUtil.isBlank(fact.getCanonicalValue())) {
                continue;
            }
            merged.putIfAbsent(fact.getSlot().name() + "::" + fact.getCanonicalValue(), fact);
        }
        return new ArrayList<>(merged.values());
    }

    private SlotCode parseSlotCode(String raw) {
        if (StrUtil.isBlank(raw) || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return SlotCode.valueOf(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private FactPolarity parsePolarity(String raw) {
        if (StrUtil.isBlank(raw) || "null".equalsIgnoreCase(raw.trim())) {
            return FactPolarity.NEUTRAL;
        }
        try {
            return FactPolarity.valueOf(raw.trim());
        } catch (Exception ignored) {
            return FactPolarity.NEUTRAL;
        }
    }

    private Double parseConfidence(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (StrUtil.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }
}
