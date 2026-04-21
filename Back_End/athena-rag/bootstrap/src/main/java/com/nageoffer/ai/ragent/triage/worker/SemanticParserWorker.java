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
import com.nageoffer.ai.ragent.triage.model.SemanticParseResult;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义解析 Worker。
 *
 * <p>职责非常单一：只把用户自然语言解析为结构化症状实体，
 * 不做追问决策，也不做风险判断。这样后面的 Worker 才能基于同一份结构化事实继续工作。</p>
 */
@Component
public class SemanticParserWorker extends AbstractStructuredTriageWorker {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+\\s*(分钟|小时|天|周|个月|月|年)|半天|昨晚开始|今天开始|刚刚开始|一整天|好几天)"
    );

    private static final Map<String, List<String>> SYMPTOM_KEYWORDS = createSymptomKeywords();

    private static final Map<String, List<String>> BODY_PART_KEYWORDS = createBodyPartKeywords();

    private static final List<String> CHARACTERISTIC_KEYWORDS = List.of(
            "绞痛", "刺痛", "钝痛", "隐痛", "阵痛", "持续性", "间歇性", "撕裂样", "压榨样"
    );

    private static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "extractedSymptoms": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "name": { "type": "string" },
                      "bodyPart": { "type": "string" },
                      "duration": { "type": "string" },
                      "severity": { "type": "string" },
                      "characteristics": {
                        "type": "array",
                        "items": { "type": "string" }
                      },
                      "accompanyingSymptoms": {
                        "type": "array",
                        "items": { "type": "string" }
                      }
                    },
                    "required": ["name"]
                  }
                }
              },
              "required": ["extractedSymptoms"]
            }
            """;

    public SemanticParserWorker(LLMService llmService, ObjectMapper objectMapper) {
        super(llmService, objectMapper);
    }

    /**
     * 执行语义解析，并把结果回填到上下文。
     *
     * <p>一旦 LLM 输出异常，系统会自动切换到轻量级规则兜底，
     * 保证后续流程至少拿到一份可用的结构化数据，而不是整个链路中断。</p>
     */
    public TriageContext execute(TriageContext context) {
        if (context == null) {
            context = new TriageContext();
        }
        context.ensureCollections();
        if (StrUtil.isBlank(context.getUserInput())) {
            context.setExtractedSymptoms(new ArrayList<>());
            return context;
        }

        String rawResponse = null;
        List<Symptom> llmSymptoms = new ArrayList<>();
        try {
            rawResponse = invokeLlm(buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D);
            llmSymptoms = parseSemanticResponse(rawResponse);
        } catch (Exception ex) {
            // 这里不向上抛异常，而是切到规则兜底，避免把“结构化抽取失败”放大成“整条问诊链路失败”。
            llmSymptoms = new ArrayList<>();
        }

        List<Symptom> heuristicSymptoms = heuristicExtract(context.getUserInput());
        context.setExtractedSymptoms(mergeSymptoms(llmSymptoms, heuristicSymptoms));
        return context;
    }

    private String buildSystemPrompt() {
        return """
                你是医疗分诊系统中的“语义解析 Worker”，只负责信息抽取，不负责诊断。
                请严格从用户原话中提取已经明确表达的信息，不允许臆测，不允许补充未出现的医学事实。

                任务要求：
                1. 识别主诉症状，并规范化为常见临床表达，例如“肚子疼”可归一为“腹痛”。
                2. 抽取症状相关的持续时间、部位、严重程度、症状性质和伴随症状。
                3. 如果信息缺失，对应字段留空或留空数组，不要编造。
                4. 只输出符合下列 JSON Schema 的 JSON，不要输出任何解释文字。

                JSON Schema:
                """ + OUTPUT_JSON_SCHEMA;
    }

    private String buildUserPrompt(TriageContext context) {
        return """
                会话ID: %s
                原始用户输入:
                %s

                请根据上面的原始输入完成结构化抽取。
                """.formatted(
                StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"),
                context.getUserInput()
        );
    }

    private List<Symptom> parseSemanticResponse(String rawResponse) {
        SemanticParseResult result = readObjectSafely(
                rawResponse,
                SemanticParseResult.class,
                SemanticParseResult.builder().build(),
                "语义解析"
        );
        List<Symptom> symptoms = result.getExtractedSymptoms();

        // 有些模型会偷懒直接返回数组，这里做一次兼容性降级。
        if ((symptoms == null || symptoms.isEmpty()) && StrUtil.contains(extractJsonPayload(rawResponse), "[")) {
            symptoms = readTypeSafely(
                    rawResponse,
                    new TypeReference<List<Symptom>>() {
                    },
                    new ArrayList<>(),
                    "语义解析-数组兼容"
            );
        }
        return normalizeSymptoms(symptoms);
    }

    private List<Symptom> heuristicExtract(String userInput) {
        List<String> detectedSymptomNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : SYMPTOM_KEYWORDS.entrySet()) {
            if (containsAny(userInput, entry.getValue())) {
                detectedSymptomNames.add(entry.getKey());
            }
        }

        if (detectedSymptomNames.isEmpty() && containsAny(userInput, List.of("疼", "痛", "难受", "不舒服"))) {
            detectedSymptomNames.add("不适");
        }

        String duration = extractDuration(userInput);
        String severity = extractSeverity(userInput);
        List<String> characteristics = extractCharacteristics(userInput);

        List<Symptom> symptoms = new ArrayList<>();
        for (String symptomName : detectedSymptomNames) {
            List<String> accompanyingSymptoms = detectedSymptomNames.stream()
                    .filter(each -> !each.equals(symptomName))
                    .toList();

            symptoms.add(Symptom.builder()
                    .name(symptomName)
                    .bodyPart(extractBodyPart(userInput, symptomName))
                    .duration(duration)
                    .severity(severity)
                    .characteristics(new ArrayList<>(characteristics))
                    .accompanyingSymptoms(new ArrayList<>(accompanyingSymptoms))
                    .build());
        }
        return normalizeSymptoms(symptoms);
    }

    private List<Symptom> mergeSymptoms(List<Symptom> llmSymptoms, List<Symptom> heuristicSymptoms) {
        LinkedHashMap<String, Symptom> merged = new LinkedHashMap<>();
        for (Symptom symptom : normalizeSymptoms(llmSymptoms)) {
            merged.put(buildSymptomKey(symptom), symptom);
        }
        for (Symptom symptom : normalizeSymptoms(heuristicSymptoms)) {
            String key = buildSymptomKey(symptom);
            Symptom existing = merged.get(key);
            if (existing == null) {
                merged.put(key, symptom);
                continue;
            }
            fillBlankFields(existing, symptom);
            mergeStringLists(existing, symptom);
        }
        return new ArrayList<>(merged.values());
    }

    private List<Symptom> normalizeSymptoms(List<Symptom> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) {
            return new ArrayList<>();
        }
        List<Symptom> normalized = new ArrayList<>();
        LinkedHashSet<String> dedupKeys = new LinkedHashSet<>();
        for (Symptom symptom : symptoms) {
            if (symptom == null || StrUtil.isBlank(symptom.getName())) {
                continue;
            }
            Symptom normalizedSymptom = Symptom.builder()
                    .name(symptom.getName().trim())
                    .bodyPart(trimToNull(symptom.getBodyPart()))
                    .duration(trimToNull(symptom.getDuration()))
                    .severity(trimToNull(symptom.getSeverity()))
                    .characteristics(normalizeStringList(symptom.getCharacteristics()))
                    .accompanyingSymptoms(normalizeStringList(symptom.getAccompanyingSymptoms()))
                    .build();
            String key = buildSymptomKey(normalizedSymptom);
            if (dedupKeys.add(key)) {
                normalized.add(normalizedSymptom);
            }
        }
        return normalized;
    }

    private void fillBlankFields(Symptom target, Symptom source) {
        if (StrUtil.isBlank(target.getBodyPart())) {
            target.setBodyPart(trimToNull(source.getBodyPart()));
        }
        if (StrUtil.isBlank(target.getDuration())) {
            target.setDuration(trimToNull(source.getDuration()));
        }
        if (StrUtil.isBlank(target.getSeverity())) {
            target.setSeverity(trimToNull(source.getSeverity()));
        }
    }

    private void mergeStringLists(Symptom target, Symptom source) {
        List<String> mergedCharacteristics = new ArrayList<>(target.getCharacteristics());
        mergedCharacteristics.addAll(source.getCharacteristics());
        target.setCharacteristics(normalizeStringList(mergedCharacteristics));

        List<String> mergedAccompanyingSymptoms = new ArrayList<>(target.getAccompanyingSymptoms());
        mergedAccompanyingSymptoms.addAll(source.getAccompanyingSymptoms());
        target.setAccompanyingSymptoms(normalizeStringList(mergedAccompanyingSymptoms));
    }

    private String extractDuration(String userInput) {
        Matcher matcher = DURATION_PATTERN.matcher(userInput);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractSeverity(String userInput) {
        if (containsAny(userInput, List.of("剧烈", "难忍", "疼得厉害", "非常严重", "特别痛"))) {
            return "重度";
        }
        if (containsAny(userInput, List.of("明显", "挺疼", "比较痛", "反复加重"))) {
            return "中度";
        }
        if (containsAny(userInput, List.of("轻微", "有点", "偶尔", "隐隐"))) {
            return "轻度";
        }
        return null;
    }

    private List<String> extractCharacteristics(String userInput) {
        List<String> result = new ArrayList<>();
        for (String keyword : CHARACTERISTIC_KEYWORDS) {
            if (userInput.contains(keyword)) {
                result.add(keyword);
            }
        }
        return normalizeStringList(result);
    }

    private String extractBodyPart(String userInput, String symptomName) {
        for (Map.Entry<String, List<String>> entry : BODY_PART_KEYWORDS.entrySet()) {
            if (containsAny(userInput, entry.getValue())) {
                return entry.getKey();
            }
        }
        if ("腹痛".equals(symptomName)) {
            return "腹部";
        }
        if ("头痛".equals(symptomName)) {
            return "头部";
        }
        if ("胸痛".equals(symptomName)) {
            return "胸部";
        }
        return null;
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String buildSymptomKey(Symptom symptom) {
        return symptom.getName() + "|" + StrUtil.blankToDefault(symptom.getBodyPart(), "");
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

    private static Map<String, List<String>> createSymptomKeywords() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("腹痛", List.of("腹痛", "肚子疼", "肚子痛", "胃痛", "胃疼", "小腹痛", "下腹痛"));
        result.put("发热", List.of("发热", "发烧", "体温高", "烧到"));
        result.put("头痛", List.of("头痛", "脑袋疼", "头疼"));
        result.put("胸痛", List.of("胸痛", "胸口痛", "心口痛"));
        result.put("恶心", List.of("恶心", "想吐"));
        result.put("呕吐", List.of("呕吐", "吐了", "吐出来"));
        result.put("腹泻", List.of("腹泻", "拉肚子", "拉稀"));
        result.put("头晕", List.of("头晕", "眩晕", "晕乎乎"));
        result.put("呼吸困难", List.of("呼吸困难", "喘不过气", "气短"));
        result.put("阴道出血", List.of("阴道出血", "流血", "见红", "出血"));
        return result;
    }

    private static Map<String, List<String>> createBodyPartKeywords() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("右下腹", List.of("右下腹"));
        result.put("左下腹", List.of("左下腹"));
        result.put("下腹部", List.of("下腹", "小腹"));
        result.put("上腹部", List.of("上腹", "胃部", "胃那里"));
        result.put("脐周", List.of("肚脐周围", "脐周"));
        result.put("胸前区", List.of("胸口", "胸前", "心口"));
        result.put("头顶部", List.of("头顶"));
        result.put("太阳穴", List.of("太阳穴"));
        return result;
    }
}
