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
import com.nageoffer.ai.ragent.triage.model.Symptom;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;

@Component
public class SemanticSymptomHeuristicHelper {

    private final ComplaintFallbackResolver complaintFallbackResolver;

    public SemanticSymptomHeuristicHelper(ComplaintFallbackResolver complaintFallbackResolver) {
        this.complaintFallbackResolver = complaintFallbackResolver;
    }

    List<Symptom> heuristicExtract(String userInput) {
        List<String> detectedSymptomNames = new ArrayList<>();
        for (var entry : SemanticParserSupport.SYMPTOM_KEYWORDS.entrySet()) {
            if (SemanticParserSupport.containsAny(userInput, entry.getValue())
                    && !SemanticParserSupport.isSymptomNegated(userInput, entry.getKey())) {
                detectedSymptomNames.add(entry.getKey());
            }
        }

        if (detectedSymptomNames.isEmpty()
                && SemanticParserSupport.containsAny(userInput, List.of("疼", "痛", "难受", "不舒服"))) {
            String fallbackComplaint = complaintFallbackResolver.resolveWeakSymptomWithBodyCue(userInput);
            detectedSymptomNames.add(fallbackComplaint == null ? "不适" : fallbackComplaint);
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

    List<Symptom> mergeSymptoms(List<Symptom> llmSymptoms, List<Symptom> heuristicSymptoms) {
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

    List<Symptom> normalizeSymptoms(List<Symptom> symptoms) {
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

    List<Symptom> filterNegatedSymptoms(List<Symptom> symptoms, String userInput) {
        if (symptoms == null || symptoms.isEmpty() || StrUtil.isBlank(userInput)) {
            return symptoms == null ? new ArrayList<>() : symptoms;
        }
        List<Symptom> filtered = new ArrayList<>();
        for (Symptom symptom : symptoms) {
            if (symptom == null || SemanticParserSupport.isSymptomNegated(userInput, symptom.getName())) {
                continue;
            }
            symptom.setAccompanyingSymptoms(symptom.getAccompanyingSymptoms().stream()
                    .filter(each -> !SemanticParserSupport.isSymptomNegated(userInput, each))
                    .toList());
            filtered.add(symptom);
        }
        return normalizeSymptoms(filtered);
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            normalized.add(value.trim());
        }
        return new ArrayList<>(normalized);
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
        Matcher matcher = SemanticParserSupport.DURATION_PATTERN.matcher(userInput);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractSeverity(String userInput) {
        if (SemanticParserSupport.containsAny(userInput, List.of("剧烈", "难忍", "疼得厉害", "非常严重", "特别痛"))) {
            return "重度";
        }
        if (SemanticParserSupport.containsAny(userInput, List.of("明显", "挺疼", "比较痛", "反复加重"))) {
            return "中度";
        }
        if (SemanticParserSupport.containsAny(userInput, List.of("轻微", "一点点", "有点", "稍微"))) {
            return "轻度";
        }
        return null;
    }

    private List<String> extractCharacteristics(String userInput) {
        List<String> characteristics = new ArrayList<>();
        for (String keyword : List.of("绞痛", "刺痛", "隐痛", "胀痛", "钝痛", "持续", "阵发性", "按压痛")) {
            if (userInput.contains(keyword)) {
                characteristics.add(keyword);
            }
        }
        return characteristics;
    }

    private String extractBodyPart(String userInput, String symptomName) {
        for (var entry : SemanticParserSupport.BODY_PART_KEYWORDS.entrySet()) {
            if (SemanticParserSupport.containsAny(userInput, entry.getValue())) {
                return entry.getKey();
            }
        }
        return "不适".equals(symptomName) ? null : null;
    }

    private String buildSymptomKey(Symptom symptom) {
        return symptom.getName() + "|" + StrUtil.blankToDefault(symptom.getBodyPart(), "");
    }

    private String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
