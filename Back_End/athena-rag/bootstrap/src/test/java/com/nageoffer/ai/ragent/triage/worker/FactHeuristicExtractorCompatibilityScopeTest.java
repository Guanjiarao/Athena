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

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactHeuristicExtractorCompatibilityScopeTest {

    @Test
    void shouldOnlyEmitCompatibilityFactForLastAskedOrPendingSlots() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-003").build();
        context.ensureCollections();
        context.appendConversation("没发热，右下腹痛一天了");
        context.setLatestUserTurn("没发热，右下腹痛一天了");
        context.setLastAskedSlots(List.of(SlotCode.FEVER_PRESENCE));
        context.setPendingSlots(List.of(SlotCode.BODY_PART));
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().build());

        List<Fact> facts = extractor.extract(context.getLatestUserTurn(), context);

        assertTrue(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.FEVER_PRESENCE));
        assertTrue(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.BODY_PART));
        assertFalse(facts.stream().anyMatch(fact -> fact.getSlot() == SlotCode.DURATION));
    }

    @Test
    void shouldStillAllowPrimaryComplaintFallbackOutsideCompatibilitySlotScope() {
        FactHeuristicExtractor extractor = new FactHeuristicExtractor(new ComplaintFallbackResolver());
        TriageContext context = TriageContext.builder().sessionId("FACT-HEURISTIC-004").build();
        context.ensureCollections();
        context.appendConversation("肚子疼");
        context.setLatestUserTurn("肚子疼");
        context.setLastAskedSlots(List.of());
        context.setPendingSlots(List.of());
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().build());

        List<Fact> facts = extractor.extract(context.getLatestUserTurn(), context);

        assertTrue(facts.stream().map(Fact::getSlot).anyMatch(slot -> slot == SlotCode.PRIMARY_SYMPTOM));
    }
}
