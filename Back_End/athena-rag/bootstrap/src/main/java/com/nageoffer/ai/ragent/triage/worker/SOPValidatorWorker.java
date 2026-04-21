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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 医疗 SOP 状态机校验 Worker。
 *
 * <p>它的职责不是“再解释一遍症状”，而是严格检查：
 * 当前这份病历信息是否已经满足最低问诊规范，
 * 如果不满足，就明确告诉编排器还差哪些字段。</p>
 */
@Component
public class SOPValidatorWorker extends AbstractStructuredTriageWorker {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+\\s*(分钟|小时|天|周|个月|月|年)|半天|昨晚开始|今天开始|刚刚开始|一整天|好几天)"
    );

    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("(3[7-9](\\.\\d)?|4\\d(\\.\\d)?)\\s*℃?");

    private static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "array",
              "items": { "type": "string" }
            }
            """;

    private static final Map<String, List<String>> SOP_RULE_MATRIX = createSopRuleMatrix();

    public SOPValidatorWorker(LLMService llmService, ObjectMapper objectMapper) {
        super(llmService, objectMapper);
    }

    /**
     * 基于硬编码 SOP 与 LLM 双重判断，得出当前还缺什么关键信息。
     *
     * <p>这里采用“LLM 主判 + 规则兜底”的组合方式：
     * - LLM 负责理解自然语言细节；
     * - 硬编码规则负责兜住医疗红线，避免漏问。</p>
     */
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
            String rawResponse = invokeLlm(
                    buildSystemPrompt(),
                    buildUserPrompt(context, matchedChecklist),
                    0.1D,
                    0.2D
            );
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
        return """
                你是医疗分诊系统中的“SOP 校验 Worker”，只负责找出当前病历里缺失的关键信息。
                你必须依据给定的医疗问诊规则，检查“原始用户输入”和“结构化症状”中是否已经出现这些字段。

                严格要求：
                1. 只输出缺失字段名的 JSON 数组，例如 ["腹痛位置","是否伴随发热"]。
                2. 如果信息已经齐全，输出 []。
                3. 不要输出解释，不要输出 Markdown，不要输出对象。
                4. 如果同一字段被不同规则重复命中，只保留一次。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    private String buildUserPrompt(TriageContext context, List<String> matchedChecklist) {
        return """
                会话ID: %s
                原始用户输入:
                %s

                结构化症状:
                %s

                当前命中的问诊 SOP:
                %s

                请仅返回仍然缺失的字段名 JSON 数组。
                """.formatted(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                context.getUserInput(),
                toJsonSafely(context.getExtractedSymptoms()),
                renderChecklist(matchedChecklist)
        );
    }

    private List<String> parseMissingFields(String rawResponse) {
        List<String> result = readTypeSafely(
                rawResponse,
                new TypeReference<List<String>>() {
                },
                new ArrayList<>(),
                "SOP校验-数组解析"
        );
        if (!result.isEmpty()) {
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
            if (!containsAny(userInput, List.of("发热", "发烧"))) {
                missingFields.add("是否伴随发热");
            }
            if (!containsAny(userInput, List.of("恶心", "想吐", "呕吐", "吐了"))) {
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

    private String renderChecklist(List<String> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return "1. 所有主诉至少需要明确：主要症状、持续时间。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < checklist.size(); i++) {
            builder.append(i + 1).append(". ").append(checklist.get(i)).append("\n");
        }
        return builder.toString().trim();
    }

    private static Map<String, List<String>> createSopRuleMatrix() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("腹痛", List.of(
                "若主诉为腹痛，必须明确：腹痛位置、持续时间、疼痛性质、是否伴随发热、是否伴随恶心或呕吐。"
        ));
        result.put("肚子", List.of(
                "若主诉为腹痛，必须明确：腹痛位置、持续时间、疼痛性质、是否伴随发热、是否伴随恶心或呕吐。"
        ));
        result.put("胸痛", List.of(
                "若主诉为胸痛，必须明确：疼痛持续时间、疼痛部位、是否伴随呼吸困难、是否伴随出汗或放射痛。"
        ));
        result.put("发热", List.of(
                "若主诉为发热，必须明确：体温、持续时间、是否伴随寒战、咳嗽或皮疹。"
        ));
        result.put("发烧", List.of(
                "若主诉为发热，必须明确：体温、持续时间、是否伴随寒战、咳嗽或皮疹。"
        ));
        result.put("头痛", List.of(
                "若主诉为头痛，必须明确：疼痛部位、持续时间、是否伴随发热、是否伴随呕吐或视物模糊。"
        ));
        result.put("出血", List.of(
                "若主诉为阴道出血或异常流血，必须明确：出血量、持续时间、是否妊娠、是否伴随腹痛或头晕。"
        ));
        result.put("见红", List.of(
                "若主诉为阴道出血或异常流血，必须明确：出血量、持续时间、是否妊娠、是否伴随腹痛或头晕。"
        ));
        return result;
    }
}
