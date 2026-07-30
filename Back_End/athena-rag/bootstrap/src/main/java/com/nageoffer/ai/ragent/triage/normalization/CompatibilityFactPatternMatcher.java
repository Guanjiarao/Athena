

package com.nageoffer.ai.ragent.triage.normalization;

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
