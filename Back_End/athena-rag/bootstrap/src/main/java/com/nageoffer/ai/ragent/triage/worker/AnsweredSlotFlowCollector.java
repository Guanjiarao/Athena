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
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.ArrayList;
import java.util.List;

public class AnsweredSlotFlowCollector {
    private final SlotAnswerInferenceHelper slotAnswerInferenceHelper;

    public AnsweredSlotFlowCollector(SlotAnswerInferenceHelper slotAnswerInferenceHelper) {
        this.slotAnswerInferenceHelper = slotAnswerInferenceHelper;
    }

    public void collectInto(TriageContext context, TurnUnderstanding understanding, String text) {
        if (context == null || understanding == null || blank(text)) {
            return;
        }
        List<SlotCode> candidates = candidateSlots(context);
        for (SlotCode slot : candidates) {
            AnsweredSlotUnderstanding inferred = slotAnswerInferenceHelper.infer(slot, text);
            if (inferred == null || hasSlot(understanding.getAnsweredSlots(), slot)) {
                continue;
            }
            inferred.setAnswersPreviousQuestion(Boolean.TRUE);
            understanding.getAnsweredSlots().add(inferred);
        }
    }

    public boolean answersLastAsked(TriageContext context, String text) {
        if (context == null || context.getLastAskedSlots() == null || blank(text)) {
            return false;
        }
        for (SlotCode slot : context.getLastAskedSlots()) {
            if (slotAnswerInferenceHelper.infer(slot, text) != null) {
                return true;
            }
        }
        return false;
    }

    private List<SlotCode> candidateSlots(TriageContext context) {
        List<SlotCode> candidates = new ArrayList<>();
        if (context.getLastAskedSlots() != null) {
            candidates.addAll(context.getLastAskedSlots());
        }
        if (context.getPendingSlots() != null) {
            for (SlotCode slot : context.getPendingSlots()) {
                if (!candidates.contains(slot)) {
                    candidates.add(slot);
                }
            }
        }
        return candidates;
    }

    private boolean hasSlot(List<AnsweredSlotUnderstanding> answeredSlots, SlotCode slot) {
        return answeredSlots != null && answeredSlots.stream().anyMatch(answered -> answered != null && answered.getSlot() == slot);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
