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
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.FactType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotManagerCompatibilityFallbackTest {

    @Test
    void shouldOnlyTreatLastAskedAndPendingFactAsCompatibilityAnsweredSlot() {
        SlotManager slotManager = new SlotManager(new SlotStateSupport());
        TriageContext context = TriageContext.builder().sessionId("SLOT-MANAGER-001").build();
        context.ensureCollections();
        context.appendConversation("没发热");
        context.setLastAskedSlots(List.of(SlotCode.FEVER_PRESENCE, SlotCode.BODY_PART));
        context.setPendingSlots(List.of(SlotCode.FEVER_PRESENCE));
        context.getFactHistory().add(Fact.builder()
                .type(FactType.FOLLOW_UP_ANSWER)
                .slot(SlotCode.FEVER_PRESENCE)
                .canonicalValue("NO")
                .polarity(FactPolarity.NEGATIVE)
                .sourceTurnIndex(0)
                .evidence("没发热")
                .build());
        context.getFactHistory().add(Fact.builder()
                .type(FactType.SLOT_EVIDENCE)
                .slot(SlotCode.BODY_PART)
                .canonicalValue("右下腹")
                .polarity(FactPolarity.NEUTRAL)
                .sourceTurnIndex(0)
                .evidence("右下腹")
                .build());

        slotManager.execute(context);

        assertEquals(1, context.getAnsweredSlots().size());
        assertTrue(context.getAnsweredSlots().contains(SlotCode.FEVER_PRESENCE));
        assertFalse(context.getAnsweredSlots().contains(SlotCode.BODY_PART));
    }

    @Test
    void shouldNotDropExistingPrimarySymptomWhenReducerComplaintTruthIsBlank() {
        SlotManager slotManager = new SlotManager(new SlotStateSupport());
        TriageContext context = TriageContext.builder().sessionId("SLOT-MANAGER-003").build();
        context.ensureCollections();
        context.getSlotState().put(com.nageoffer.ai.ragent.triage.model.SlotValue.builder()
                .slot(SlotCode.PRIMARY_SYMPTOM)
                .value("腹痛")
                .status(com.nageoffer.ai.ragent.triage.model.SlotStatus.FILLED)
                .evidence("seed")
                .build());
        context.appendStateReducerResult(com.nageoffer.ai.ragent.triage.model.StateReducerResult.builder()
                .reducedSlots(java.util.Map.of())
                .complaintTruth(null)
                .build());

        slotManager.execute(context);

        assertEquals("腹痛", context.getSlotState().get(SlotCode.PRIMARY_SYMPTOM).getValue());
    }
}
