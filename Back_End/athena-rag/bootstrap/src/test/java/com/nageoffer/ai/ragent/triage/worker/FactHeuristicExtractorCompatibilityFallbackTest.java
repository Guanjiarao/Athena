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
import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactHeuristicExtractorCompatibilityFallbackTest {

    @Test
    void shouldSkipHeuristicFactsWhenTurnUnderstandingAlreadyCoversSlotAndComplaint() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-001").build();
        context.ensureCollections();
        context.appendConversation("右下腹痛一天了，没发热");
        context.setLatestUserTurn("右下腹痛一天了，没发热");
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder()
                .primaryComplaint(ComplaintUnderstanding.builder().value("腹痛").evidence("腹部痛").build())
                .answeredSlots(List.of(
                        AnsweredSlotUnderstanding.builder().slot(SlotCode.BODY_PART).normalizedValue("右下腹").assertion(AssertionStatus.PRESENT).build(),
                        AnsweredSlotUnderstanding.builder().slot(SlotCode.DURATION).normalizedValue("一天").assertion(AssertionStatus.PRESENT).build(),
                        AnsweredSlotUnderstanding.builder().slot(SlotCode.FEVER_PRESENCE).normalizedValue("NO").assertion(AssertionStatus.ABSENT).build()
                ))
                .build());

        List<Fact> facts = extractor.extract(context.getLatestUserTurn(), context);

        assertFalse(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.BODY_PART));
        assertFalse(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.DURATION));
        assertFalse(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.FEVER_PRESENCE));
        assertFalse(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.PRIMARY_SYMPTOM));
    }

    @Test
    void shouldStillProduceCompatibilityFactWhenTurnUnderstandingDoesNotCoverAskedOrPendingSlot() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-002").build();
        context.ensureCollections();
        context.appendConversation("没发热");
        context.setLatestUserTurn("没发热");
        context.setLastAskedSlots(List.of(SlotCode.FEVER_PRESENCE));
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().build());

        List<Fact> facts = extractor.extract(context.getLatestUserTurn(), context);

        assertTrue(facts.stream().map(Fact::getSlot).anyMatch(slot -> slot == SlotCode.FEVER_PRESENCE));
    }

    @Test
    void shouldRestrictPrimaryComplaintFallbackToAskedOrPendingScope() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-003").build();
        context.ensureCollections();
        context.appendConversation("胸口痛一整天");
        context.setLatestUserTurn("胸口痛一整天");
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().build());

        List<Fact> factsWithoutScope = extractor.extract(context.getLatestUserTurn(), context);
        assertFalse(factsWithoutScope.stream().anyMatch(fact -> fact.getSlot() == SlotCode.PRIMARY_SYMPTOM));

        context.setPendingSlots(List.of(SlotCode.PRIMARY_SYMPTOM));
        List<Fact> factsWithScope = extractor.extract(context.getLatestUserTurn(), context);
        assertTrue(factsWithScope.stream().anyMatch(fact -> fact.getSlot() == SlotCode.PRIMARY_SYMPTOM));
    }

    @Test
    void shouldProduceCompatibilityDurationFactForSpannedNaturalExpression() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-004").build();
        context.ensureCollections();
        context.appendConversation("我从昨天晚上发烧到现在还肚子疼");
        context.setLatestUserTurn("我从昨天晚上发烧到现在还肚子疼");
        context.setLastAskedSlots(List.of(SlotCode.DURATION));
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().build());

        List<Fact> facts = extractor.extract(context.getLatestUserTurn(), context);

        assertTrue(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.DURATION
                && "昨天晚上到现在".equals(fact.getCanonicalValue())));
    }
}
