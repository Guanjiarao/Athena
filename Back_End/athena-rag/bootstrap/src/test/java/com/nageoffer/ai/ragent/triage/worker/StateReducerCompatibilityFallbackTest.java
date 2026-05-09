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

import com.nageoffer.ai.ragent.triage.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StateReducerCompatibilityFallbackTest {
    @Test
    void shouldPreferTurnUnderstandingOverFactFallbackForAnsweredSlot() {
        StateReducer reducer = new StateReducer();
        TriageContext context = TriageContext.builder().sessionId("STATE-REDUCER-001").build();
        context.ensureCollections();
        context.appendConversation("没发热");
        context.getSlotState().put(SlotValue.builder().slot(SlotCode.FEVER_PRESENCE).value("YES").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        context.setPendingSlots(List.of(SlotCode.FEVER_PRESENCE));
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().answeredSlots(List.of(AnsweredSlotUnderstanding.builder().slot(SlotCode.FEVER_PRESENCE).assertion(AssertionStatus.ABSENT).evidence("没发热").build())).build());
        context.getFactHistory().add(Fact.builder().type(FactType.FOLLOW_UP_ANSWER).slot(SlotCode.FEVER_PRESENCE).canonicalValue("YES").polarity(FactPolarity.POSITIVE).sourceTurnIndex(0).evidence("发热").build());
        reducer.execute(context);
        StateReducerResult result = context.getLatestStateReducerResult();
        assertEquals("NO", result.getReducedSlots().get(SlotCode.FEVER_PRESENCE).getValue());
        assertEquals(SlotStatus.NEGATED, result.getReducedSlots().get(SlotCode.FEVER_PRESENCE).getStatus());
        assertFalse(result.getPendingCandidates().contains(SlotCode.FEVER_PRESENCE));
    }

    @Test
    void shouldApplyCorrectionToExplicitResolvedSlot() {
        StateReducer reducer = new StateReducer();
        TriageContext context = TriageContext.builder().sessionId("STATE-REDUCER-002").build();
        context.ensureCollections();
        context.appendConversation("不是左下腹，是右下腹");
        context.getSlotState().put(SlotValue.builder().slot(SlotCode.BODY_PART).value("左下腹").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        context.setPendingSlots(List.of(SlotCode.BODY_PART));
        context.setLatestTurnUnderstanding(TurnUnderstanding.builder().corrections(List.of(CorrectionUnderstanding.builder().target(CorrectionTarget.SLOT_VALUE).slot(SlotCode.BODY_PART).rejectValue("左下腹").confirmValue("右下腹").evidence("不是左下腹，是右下腹").build())).build());
        reducer.execute(context);
        StateReducerResult result = context.getLatestStateReducerResult();
        assertEquals("右下腹", result.getReducedSlots().get(SlotCode.BODY_PART).getValue());
        assertEquals(SlotStatus.CORRECTED, result.getReducedSlots().get(SlotCode.BODY_PART).getStatus());
        assertFalse(result.getPendingCandidates().contains(SlotCode.BODY_PART));
    }
}
