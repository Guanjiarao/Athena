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
import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.FactType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

import java.util.List;

final class CompatibilityFactPatternMatcher {

    Fact buildPresenceFact(String text, TriageContext context, SlotCode slotCode,
                           List<String> positiveKeywords, List<String> negativeKeywords, int turnIndex) {
        if (containsAny(text, negativeKeywords)) {
            return Fact.builder().type(FactType.FOLLOW_UP_ANSWER).slot(slotCode).canonicalValue("NO").polarity(FactPolarity.NEGATIVE)
                    .confidence(0.9D).evidence(text).sourceTurnIndex(turnIndex).sourceText(context.getLatestUserTurn()).build();
        }
        if (containsAny(text, positiveKeywords)) {
            return Fact.builder().type(FactType.FOLLOW_UP_ANSWER).slot(slotCode).canonicalValue("YES").polarity(FactPolarity.POSITIVE)
                    .confidence(0.85D).evidence(text).sourceTurnIndex(turnIndex).sourceText(context.getLatestUserTurn()).build();
        }
        return null;
    }

    Fact basicFact(SlotCode slotCode, String canonicalValue, FactPolarity polarity, String evidence, int turnIndex, String latestTurn) {
        return Fact.builder().type(FactType.SLOT_EVIDENCE).slot(slotCode).canonicalValue(canonicalValue).polarity(polarity)
                .confidence(0.85D).evidence(evidence).sourceTurnIndex(turnIndex).sourceText(latestTurn).build();
    }

    Fact primaryFact(String canonicalValue, int turnIndex, String latestTurn) {
        return Fact.builder().type(FactType.PRIMARY_SIGNAL).slot(SlotCode.PRIMARY_SYMPTOM).canonicalValue(canonicalValue)
                .polarity(FactPolarity.POSITIVE).confidence(0.9D).evidence(latestTurn).sourceTurnIndex(turnIndex).sourceText(latestTurn).build();
    }

    String extractDuration(String userInput) {
        return SemanticParserSupport.extractDuration(userInput);
    }

    String extractTemperature(String userInput) {
        var matcher = java.util.regex.Pattern.compile("(\\d{2}(\\.\\d)?度多?|\\d{2}度半)").matcher(userInput);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    String extractBodyPart(String userInput) {
        for (var entry : SemanticParserSupport.BODY_PART_KEYWORDS.entrySet()) {
            if (containsAny(userInput, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
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
}
