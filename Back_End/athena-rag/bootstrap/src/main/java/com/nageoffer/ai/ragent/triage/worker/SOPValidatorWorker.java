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
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.prompt.SopValidatorPromptTemplates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SOPValidatorWorker extends AbstractStructuredTriageWorker {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+\\s*(分钟|小时|天|周|个月|月|年)|半天|昨晚开始|今天开始|刚刚开始|一整天|好几天)"
    );

    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("(3[7-9](\\.\\d)?|4\\d(\\.\\d)?)\\s*℃?");

    private static final Map<String, List<String>> SOP_RULE_MATRIX = createSopRuleMatrix();

    public SOPValidatorWorker(LLMService llmService, ObjectMapper objectMapper) {
        super(llmService, objectMapper);
    }

    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();

        if (StrUtil.isBlank(context.getUserInput())) {
            context.setMissingFields(List.of("主诉症状", "持续时间"));
            return context;
        }

        List<String> matchedChecklist = buildMatchedChecklist(context);
        List<String> llmMissingFields = new ArrayList<>();
        try {
            String rawResponse = invokeLlm(buildSystemPrompt(), buildUserPrompt(context, matchedChecklist), 0.1D, 0.2D);
            llmMissingFields = parseMissingFields(rawResponse);
        } catch (Exception ignored) {
            llmMissingFields = new ArrayList<>();
        }

        List<String> ruleFallbackFields = heuristicMissingFields(context);
        llmMissingFields.addAll(ruleFallbackFields);
        context.setMissingFields(normalizeStringList(llmMissingFields));
        return context;
    }

    private String buildSystemPrompt() {
        return SopValidatorPromptTemplates.systemPrompt();
    }

    private String buildUserPrompt(TriageContext context, List<String> matchedChecklist) {
        return SopValidatorPromptTemplates.userPrompt(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                context.getUserInput(),
                toJsonSafely(context.getExtractedSymptoms()),
                matchedChecklist
        );
    }

    private List<String> parseMissingFields(String rawResponse) {
        String payload = extractJsonPayload(rawResponse);
        if (StrUtil.isBlank(payload)) {
            return new ArrayList<>();
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("[")) {
            List<String> result = readTypeSafely(
                    rawResponse,
                    new TypeReference<List<String>>() {
                    },
                    new ArrayList<>(),
                    "SOP校验-数组解析"
            );
            return normalizeStringList(result);
        }

        Map<String, Object> objectFallback = readTypeSafely(
                rawResponse,
                new TypeReference<Map<String, Object>>() {
                },
                new LinkedHashMap<>(),
                "SOP校验-对象兼容"
        );
        Object missingFields = objectFallback.get("missingFields");
        if (missingFields instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object item : list) {
                normalized.add(String.valueOf(item));
            }
            return normalizeStringList(normalized);
        }
        return new ArrayList<>();
    }

    private List<String> buildMatchedChecklist(TriageContext context) {
        LinkedHashSet<String> checklist = new LinkedHashSet<>();
        checklist.add("所有主诉至少需要明确：主要症状、持续时间。");
        checklist.add("凡是涉及疼痛，至少需要明确：疼痛部位、疼痛程度或疼痛性质。");

        String combinedText = buildCombinedText(context);
        for (Map.Entry<String, List<String>> entry : SOP_RULE_MATRIX.entrySet()) {
            if (combinedText.contains(entry.getKey())) {
                checklist.addAll(entry.getValue());
            }
        }

        if (checklist.size() <= 2) {
            checklist.add("若存在明显加重、反复发作或影响日常生活，需要补充症状变化趋势。");
        }
        return new ArrayList<>(checklist);
    }

    private List<String> heuristicMissingFields(TriageContext context) {
        LinkedHashSet<String> missingFields = new LinkedHashSet<>();
        String userInput = StrUtil.blankToDefault(context.getUserInput(), "");
        List<Symptom> symptoms = context.getExtractedSymptoms() == null ? List.of() : context.getExtractedSymptoms();

        if (symptoms.isEmpty()) {
            missingFields.add("主诉症状");
        }
        if (!DURATION_PATTERN.matcher(userInput).find() && symptoms.stream().noneMatch(each -> StrUtil.isNotBlank(each.getDuration()))) {
            missingFields.add("持续时间");
        }

        boolean hasPain = containsSymptom(symptoms, "腹痛")
                || containsSymptom(symptoms, "头痛")
                || containsSymptom(symptoms, "胸痛")
                || userInput.contains("疼")
                || userInput.contains("痛");
        boolean hasBodyPart = symptoms.stream().anyMatch(each -> StrUtil.isNotBlank(each.getBodyPart()));
        if (hasPain && !hasBodyPart) {
            missingFields.add("疼痛部位");
        }

        if (containsSymptom(symptoms, "腹痛")) {
            if (!hasFeverAnswer(userInput)) {
                missingFields.add("是否伴随发热");
            }
            if (!hasNauseaOrVomitingAnswer(userInput)) {
                missingFields.add("是否伴随恶心或呕吐");
            }
        }

        if (containsSymptom(symptoms, "胸痛") && !containsAny(userInput, List.of("呼吸困难", "喘不过气", "气短"))) {
            missingFields.add("是否伴随呼吸困难");
        }

        if (containsSymptom(symptoms, "发热") && !TEMPERATURE_PATTERN.matcher(userInput).find()) {
            missingFields.add("体温");
        }

        if (containsSymptom(symptoms, "阴道出血") && !containsAny(userInput, List.of("怀孕", "妊娠", "月经", "经期"))) {
            missingFields.add("是否妊娠");
        }

        return new ArrayList<>(missingFields);
    }

    private String buildCombinedText(TriageContext context) {
        StringBuilder combined = new StringBuilder();
        combined.append(StrUtil.blankToDefault(context.getUserInput(), ""));
        if (context.getExtractedSymptoms() != null) {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (symptom == null) {
                    continue;
                }
                combined.append(" ").append(StrUtil.blankToDefault(symptom.getName(), ""));
                combined.append(" ").append(StrUtil.blankToDefault(symptom.getBodyPart(), ""));
                combined.append(" ").append(StrUtil.blankToDefault(symptom.getDuration(), ""));
            }
        }
        return combined.toString();
    }

    private boolean containsSymptom(List<Symptom> symptoms, String symptomName) {
        if (symptoms == null || symptoms.isEmpty()) {
            return false;
        }
        return symptoms.stream().anyMatch(each -> each != null && symptomName.equals(each.getName()));
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFeverAnswer(String text) {
        return containsAny(text, List.of(
                "发热", "发烧", "没有发热", "没发热", "无发热", "没有发烧", "没发烧", "无发烧", "没有明显发烧"
        ));
    }

    private boolean hasNauseaOrVomitingAnswer(String text) {
        return containsAny(text, List.of(
                "恶心", "想吐", "呕吐", "吐了", "没有恶心", "没恶心", "无恶心", "没有吐", "没吐", "无呕吐"
        ));
    }

    private static Map<String, List<String>> createSopRuleMatrix() {
        Map<String, List<String>> matrix = new LinkedHashMap<>();
        matrix.put("腹痛", List.of("腹痛需要补充：疼痛部位、是否伴随发热、是否伴随恶心或呕吐。"));
        matrix.put("胸痛", List.of("胸痛需要补充：疼痛部位、是否伴随呼吸困难。"));
        matrix.put("发热", List.of("发热需要补充：体温。"));
        matrix.put("阴道出血", List.of("阴道出血需要补充：是否妊娠。"));
        return matrix;
    }
}
