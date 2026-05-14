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

import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.SlotCode;

import java.util.List;
import java.util.Locale;

public class SlotAnswerInferenceHelper {
    private static final List<String> UNCERTAIN_CUES = List.of("说不清", "不确定", "不太确定", "拿不准", "不好说", "不清楚", "好像", "可能", "是不是");

    private final ComplaintFallbackResolver complaintFallbackResolver;

    public SlotAnswerInferenceHelper(ComplaintFallbackResolver complaintFallbackResolver) {
        this.complaintFallbackResolver = complaintFallbackResolver;
    }

    public AnsweredSlotUnderstanding infer(SlotCode slot, String text) {
        if (slot == null || blank(text)) return null;
        return switch (slot) {
            case FEVER_PRESENCE -> boolSlot(slot, text, List.of("发热", "发烧"));
            case NAUSEA_PRESENCE -> boolSlot(slot, text, List.of("恶心", "想吐"));
            case VOMITING_PRESENCE -> boolSlot(slot, text, List.of("呕吐", "吐", "吐了"));
            case DYSPNEA_PRESENCE -> boolSlot(slot, text, List.of("呼吸困难", "喘不过气", "气短", "喘不上来", "喘不上气", "上不来气", "透不过气"));
            case BLEEDING_PRESENCE -> boolSlot(slot, text, List.of("出血", "见红", "流血"));
            case SEIZURE_PRESENCE -> boolSlot(slot, text, List.of("抽搐", "惊厥"));
            case DIARRHEA_PRESENCE -> boolSlot(slot, text, List.of("腹泻", "拉肚子", "拉稀"));
            case DURATION -> durationSlot(slot, text);
            case BODY_PART -> bodyPartSlot(slot, text);
            case PRIMARY_SYMPTOM -> primarySymptomSlot(slot, text);
            default -> null;
        };
    }

    private AnsweredSlotUnderstanding boolSlot(SlotCode slot, String text, List<String> keywords) {
        if (!SemanticParserSupport.containsAny(text, keywords)) return null;
        AssertionStatus status = hasUncertaintyCue(text) ? AssertionStatus.UNKNOWN : negated(text, keywords) ? AssertionStatus.ABSENT : AssertionStatus.PRESENT;
        String normalized = status == AssertionStatus.ABSENT ? "NO" : status == AssertionStatus.UNKNOWN ? "UNKNOWN" : "YES";
        return AnsweredSlotUnderstanding.builder().slot(slot).rawValue(text).normalizedValue(normalized).assertion(status).confidence(status == AssertionStatus.UNKNOWN ? 0.6D : 0.8D).evidence(text).build();
    }

    private AnsweredSlotUnderstanding durationSlot(SlotCode slot, String text) {
        String value = SemanticParserSupport.extractDuration(text);
        if (blank(value)) return null;
        return AnsweredSlotUnderstanding.builder().slot(slot).rawValue(value).normalizedValue(value).assertion(AssertionStatus.PRESENT).confidence(0.85D).evidence(text).build();
    }

    private AnsweredSlotUnderstanding bodyPartSlot(SlotCode slot, String text) {
        for (var entry : SemanticParserSupport.BODY_PART_KEYWORDS.entrySet()) {
            if (SemanticParserSupport.containsAny(text, entry.getValue())) {
                return AnsweredSlotUnderstanding.builder().slot(slot).rawValue(entry.getKey()).normalizedValue(entry.getKey()).assertion(AssertionStatus.PRESENT).confidence(0.8D).evidence(text).build();
            }
        }
        return null;
    }

    private AnsweredSlotUnderstanding primarySymptomSlot(SlotCode slot, String text) {
        String value = resolvePrimaryComplaintValue(text);
        return blank(value) ? null : AnsweredSlotUnderstanding.builder().slot(slot).rawValue(value).normalizedValue(value).assertion(AssertionStatus.PRESENT).confidence(0.8D).evidence(text).build();
    }

    private String resolvePrimaryComplaintValue(String text) {
        String value = complaintFallbackResolver.resolvePrimaryComplaint(text);
        if (blank(value)) value = complaintFallbackResolver.resolveWeakSymptomWithBodyCue(text);
        return trim(value);
    }

    private boolean negated(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains("没有" + keyword) || lower.contains("没" + keyword) || lower.contains("不" + keyword) || lower.contains("无" + keyword)) return true;
        }
        return false;
    }

    private boolean hasUncertaintyCue(String text) {
        return SemanticParserSupport.containsAny(text, UNCERTAIN_CUES);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }
}
