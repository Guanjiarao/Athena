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

import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPlanSupportSemanticDiscoveryTest {

    @Test
    void shouldDiscoverAbdominalPainFollowUpGapsFromStructuredSymptoms() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        TriageContext context = TriageContext.builder()
                .slotState(SlotState.empty())
                .extractedSymptoms(List.of(Symptom.builder().name("腹痛").build()))
                .build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.BODY_PART));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.PAIN_SEVERITY));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.FEVER_PRESENCE));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.NAUSEA_PRESENCE));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.VOMITING_PRESENCE));
    }

    @Test
    void shouldDiscoverChestPainRiskGapFromFactBackedPrimarySignal() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        TriageContext context = TriageContext.builder()
                .slotState(SlotState.empty())
                .factHistory(List.of(com.nageoffer.ai.ragent.triage.model.Fact.builder()
                        .slot(SlotCode.PRIMARY_SYMPTOM)
                        .canonicalValue("胸痛")
                        .build()))
                .build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.DYSPNEA_PRESENCE));
    }

    @Test
    void shouldDiscoverTemperatureGapFromFeverSemanticSignal() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        TriageContext context = TriageContext.builder()
                .slotState(SlotState.empty())
                .extractedSymptoms(List.of(Symptom.builder().name("发热").build()))
                .build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.TEMPERATURE));
    }

    @Test
    void shouldDiscoverPregnancyStatusGapFromPregnancyBleedingRiskSignal() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        TriageContext context = TriageContext.builder()
                .slotState(SlotState.empty())
                .riskSignalState(List.of(com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding.builder()
                        .type(com.nageoffer.ai.ragent.triage.model.RiskSignalType.PREGNANCY_RELATED_BLEEDING)
                        .assertion(com.nageoffer.ai.ragent.triage.model.AssertionStatus.PRESENT)
                        .build()))
                .build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.PREGNANCY_STATUS));
    }

    @Test
    void shouldDiscoverPrimaryComplaintGapFromConsciousnessRiskSignal() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        TriageContext context = TriageContext.builder()
                .slotState(SlotState.empty())
                .riskSignalState(List.of(com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding.builder()
                        .type(com.nageoffer.ai.ragent.triage.model.RiskSignalType.ALTERED_CONSCIOUSNESS)
                        .assertion(com.nageoffer.ai.ragent.triage.model.AssertionStatus.PRESENT)
                        .build()))
                .build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.PRIMARY_SYMPTOM));
    }
}
