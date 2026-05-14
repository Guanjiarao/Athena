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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.eval.TriageEvalRealExecutor;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.CorrectionTarget;
import com.nageoffer.ai.ragent.triage.model.CorrectionUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnIntent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnUnderstandingWorkerSemanticBoundaryTest {
    private final LLMService llm = TriageEvalRealExecutor.heuristicLlmStub();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldGroundNaturalDurationExpressionDuringFollowUpAnswer() {
        TurnUnderstandingWorker worker = new TurnUnderstandingWorker(llm, mapper, new ComplaintFallbackResolver());
        TriageContext c = TriageContext.builder().sessionId("duration-carry").latestUserTurn("我从昨天晚上发烧到现在还肚子疼").build();
        c.ensureCollections();
        c.setLastAskedSlots(List.of(SlotCode.DURATION));
        c.setPendingSlots(List.of(SlotCode.DURATION, SlotCode.BODY_PART));
        c.getSlotState().put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("腹痛").status(SlotStatus.FILLED).evidence("seed").updatedAt(Instant.now()).build());
        worker.execute(c);

        assertEquals(TurnIntent.ANSWER_FOLLOW_UP, c.getLatestTurnUnderstanding().getIntent());
        assertTrue(c.getLatestTurnUnderstanding().getAnsweredSlots().stream()
                .anyMatch(answered -> answered.getSlot() == SlotCode.DURATION
                        && "昨天晚上到现在".equals(answered.getNormalizedValue())));
        assertEquals("腹痛", c.getLatestTurnUnderstanding().getPrimaryComplaint().getValue());

        StateReducer reducer = new StateReducer();
        reducer.execute(c);

        SlotManager slotManager = new SlotManager(new SlotStateSupport());
        slotManager.execute(c);

        assertTrue(c.getAnsweredSlots().contains(SlotCode.DURATION));
        assertFalse(c.getPendingSlots().contains(SlotCode.DURATION));
        SlotValue duration = c.getSlotState().get(SlotCode.DURATION);
        assertNotNull(duration);
        assertEquals("昨天晚上到现在", duration.getValue());
        assertEquals(SlotStatus.FILLED, duration.getStatus());
    }

    @Test
    void shouldCarryForwardPrimaryComplaintDuringFollowUpAnswer() {
        TurnUnderstandingWorker worker = new TurnUnderstandingWorker(llm, mapper, new ComplaintFallbackResolver());
        TriageContext c = TriageContext.builder().sessionId("carry").latestUserTurn("昨天晚上开始").build();
        c.ensureCollections();
        c.setLastAskedSlots(List.of(SlotCode.DURATION));
        c.getSlotState().put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("腹痛").status(SlotStatus.FILLED).evidence("seed").updatedAt(Instant.now()).build());
        worker.execute(c);
        assertEquals("腹痛", c.getLatestTurnUnderstanding().getPrimaryComplaint().getValue());
        assertEquals(TurnIntent.ANSWER_FOLLOW_UP, c.getLatestTurnUnderstanding().getIntent());
    }

    @Test
    void shouldCarryComplaintAndUncertainRiskAnswerTogether() {
        TurnUnderstandingWorker worker = new TurnUnderstandingWorker(llm, mapper, new ComplaintFallbackResolver());
        TriageContext c = TriageContext.builder().sessionId("risk-carry").latestUserTurn("说不清是不是喘不上气").build();
        c.ensureCollections();
        c.setLastAskedSlots(List.of(SlotCode.DYSPNEA_PRESENCE));
        c.getSlotState().put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("胸部不适").status(SlotStatus.FILLED).evidence("seed").updatedAt(Instant.now()).build());
        worker.execute(c);
        assertEquals("胸部不适", c.getLatestTurnUnderstanding().getPrimaryComplaint().getValue());
        assertEquals(TurnIntent.ANSWER_FOLLOW_UP, c.getLatestTurnUnderstanding().getIntent());
        assertTrue(c.getLatestTurnUnderstanding().getAnsweredSlots().stream()
                .anyMatch(answered -> answered.getSlot() == SlotCode.DYSPNEA_PRESENCE
                        && answered.getAssertion() == AssertionStatus.UNKNOWN));
    }

    @Test
    void shouldResolveCorrectionStateFirstToExistingSlot() {
        TurnUnderstandingWorker worker = new TurnUnderstandingWorker(llm, mapper, new ComplaintFallbackResolver());
        TriageContext c = TriageContext.builder().sessionId("corr").latestUserTurn("不是左下腹，是右下腹").build();
        c.ensureCollections();
        c.getSlotState().put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("腹痛").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        c.getSlotState().put(SlotValue.builder().slot(SlotCode.BODY_PART).value("左下腹").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        worker.execute(c);
        CorrectionUnderstanding corr = c.getLatestTurnUnderstanding().getCorrections().get(0);
        assertEquals(CorrectionTarget.SLOT_VALUE, corr.getTarget());
        assertEquals(SlotCode.BODY_PART, corr.getSlot());
        assertEquals("右下腹", corr.getConfirmValue());
        assertEquals("腹痛", c.getLatestTurnUnderstanding().getPrimaryComplaint().getValue());
    }
}
