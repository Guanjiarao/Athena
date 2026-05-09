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
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class FactHeuristicExtractor {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    private final ComplaintFallbackResolver complaintFallbackResolver;
    private final CompatibilityFactScope compatibilityFactScope;
    private final CompatibilityFactPatternMatcher patternMatcher;

    @Autowired
    public FactHeuristicExtractor(ComplaintFallbackResolver complaintFallbackResolver) {
        this(complaintFallbackResolver, new CompatibilityFactScope(), new CompatibilityFactPatternMatcher());
    }

    FactHeuristicExtractor(ComplaintFallbackResolver complaintFallbackResolver,
                           CompatibilityFactScope compatibilityFactScope,
                           CompatibilityFactPatternMatcher patternMatcher) {
        this.complaintFallbackResolver = complaintFallbackResolver;
        this.compatibilityFactScope = compatibilityFactScope;
        this.patternMatcher = patternMatcher;
    }

    List<Fact> extract(String latestTurn, TriageContext context) {
        List<Fact> facts = new ArrayList<>();
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        int turnIndex = Math.max(0, context.getConversationHistory().size() - 1);

        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.FEVER_PRESENCE,
                List.of("发热", "发烧", "烧"),
                List.of("没有发热", "没发热", "无发热", "不发热", "没有发烧", "没发烧", "无发烧", "不发烧", "不烧"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.NAUSEA_PRESENCE,
                List.of("恶心", "想吐"), List.of("没有恶心", "没恶心", "无恶心", "不恶心"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.VOMITING_PRESENCE,
                List.of("呕吐", "吐了", "吐出来", "吐"), List.of("没有呕吐", "没呕吐", "无呕吐", "没有吐", "没吐", "不吐"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.DYSPNEA_PRESENCE,
                List.of("呼吸困难", "喘不过气", "喘不过来气", "喘不上来", "气短", "上不来气", "透不过气"),
                List.of("没有呼吸困难", "没呼吸困难", "无呼吸困难", "没有气短", "没气短", "不气短", "没有喘不过气", "没喘不过气", "没有喘不过来气", "没喘不过来气", "没有喘不上来", "没喘不上来", "没有上不来气", "没上不来气", "没有透不过气", "没透不过气"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.BLEEDING_PRESENCE,
                List.of("出血", "见红", "大出血", "流血"), List.of("没出血", "没有出血", "无出血", "没见红", "没有见红", "无见红", "没流血", "没有流血", "无流血"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.PREGNANCY_STATUS,
                List.of("怀孕", "妊娠"), List.of("没怀孕", "没有怀孕", "未怀孕", "不是怀孕"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.SEIZURE_PRESENCE,
                List.of("抽搐", "惊厥"), List.of("没有抽搐", "没抽搐", "无抽搐"), turnIndex);
        addPresenceFact(facts, dedup, latestTurn, context, SlotCode.DIARRHEA_PRESENCE,
                List.of("拉肚子", "腹泻"), List.of("没拉肚子", "没有拉肚子", "无拉肚子", "没有腹泻", "没腹泻", "无腹泻"), turnIndex);

        addStructuredCompatibilityFact(facts, dedup, context, SlotCode.DURATION,
                patternMatcher.extractDuration(latestTurn), turnIndex, latestTurn);
        addStructuredCompatibilityFact(facts, dedup, context, SlotCode.TEMPERATURE,
                patternMatcher.extractTemperature(latestTurn), turnIndex, latestTurn);
        addStructuredCompatibilityFact(facts, dedup, context, SlotCode.BODY_PART,
                patternMatcher.extractBodyPart(latestTurn), turnIndex, latestTurn);

        String fallbackPrimaryComplaint = complaintFallbackResolver.resolvePrimaryComplaint(latestTurn);
        if (fallbackPrimaryComplaint != null
                && compatibilityFactScope.shouldEmitCompatibilityFact(context, SlotCode.PRIMARY_SYMPTOM)
                && !compatibilityFactScope.hasPrimaryComplaintUnderstanding(context)) {
            addIfAbsent(facts, dedup, patternMatcher.primaryFact(fallbackPrimaryComplaint, turnIndex, latestTurn));
        }
        return facts;
    }

    private void addPresenceFact(List<Fact> facts, LinkedHashSet<String> dedup, String latestTurn, TriageContext context,
                                 SlotCode slotCode, List<String> positiveKeywords, List<String> negativeKeywords, int turnIndex) {
        if (!compatibilityFactScope.shouldEmitCompatibilityFact(context, slotCode)
                || compatibilityFactScope.isAnsweredByTurnUnderstanding(context, slotCode)) {
            return;
        }
        addIfAbsent(facts, dedup,
                patternMatcher.buildPresenceFact(latestTurn, context, slotCode, positiveKeywords, negativeKeywords, turnIndex));
    }

    private void addStructuredCompatibilityFact(List<Fact> facts, LinkedHashSet<String> dedup, TriageContext context,
                                                SlotCode slotCode, String canonicalValue, int turnIndex, String latestTurn) {
        if (StrUtil.isBlank(canonicalValue)
                || !compatibilityFactScope.shouldEmitCompatibilityFact(context, slotCode)
                || compatibilityFactScope.isAnsweredByTurnUnderstanding(context, slotCode)) {
            return;
        }
        addIfAbsent(facts, dedup,
                patternMatcher.basicFact(slotCode, canonicalValue, FactPolarity.NEUTRAL, canonicalValue, turnIndex, latestTurn));
    }

    private void addIfAbsent(List<Fact> facts, LinkedHashSet<String> dedup, Fact fact) {
        if (fact == null || fact.getSlot() == null || StrUtil.isBlank(fact.getCanonicalValue())) {
            return;
        }
        String key = fact.getSlot().name() + "::" + fact.getCanonicalValue();
        if (dedup.add(key)) {
            facts.add(fact);
        }
    }
}
