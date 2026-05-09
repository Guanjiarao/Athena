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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class CompatibilitySlotFallback {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    void mergeFactsIntoSlotState(TriageContext context, SlotStateSupport slotStateSupport) {
        if (context == null || slotStateSupport == null || context.getFactHistory() == null) {
            return;
        }
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null || fact.getCanonicalValue() == null) {
                continue;
            }
            slotStateSupport.mergeFact(context.getSlotState(), fact);
        }
    }

    List<SlotCode> resolveCompatibilityAnsweredSlots(TriageContext context) {
        if (context == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<SlotCode> answered = new LinkedHashSet<>();
        List<SlotCode> lastAsked = context.getLastAskedSlots() == null ? List.of() : context.getLastAskedSlots();
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        if (lastAsked.isEmpty() || pendingSlots.isEmpty() || context.getFactHistory() == null) {
            return new ArrayList<>();
        }
        int latestTurnIndex = Math.max(0, context.getConversationHistory().size() - 1);
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null) {
                continue;
            }
            if (fact.getSourceTurnIndex() != latestTurnIndex) {
                continue;
            }
            if (lastAsked.contains(fact.getSlot()) && pendingSlots.contains(fact.getSlot())) {
                answered.add(fact.getSlot());
            }
        }
        return new ArrayList<>(answered);
    }
}
