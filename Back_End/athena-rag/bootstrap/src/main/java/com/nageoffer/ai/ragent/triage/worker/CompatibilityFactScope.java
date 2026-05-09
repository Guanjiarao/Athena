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
import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.List;

final class CompatibilityFactScope {

    boolean shouldEmitCompatibilityFact(TriageContext context, SlotCode slotCode) {
        if (context == null || slotCode == null) {
            return false;
        }
        List<SlotCode> lastAskedSlots = context.getLastAskedSlots() == null ? List.of() : context.getLastAskedSlots();
        if (lastAskedSlots.contains(slotCode)) {
            return true;
        }
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        return pendingSlots.contains(slotCode);
    }

    boolean isAnsweredByTurnUnderstanding(TriageContext context, SlotCode slotCode) {
        if (context == null || slotCode == null) {
            return false;
        }
        TurnUnderstanding understanding = context.getLatestTurnUnderstanding();
        if (understanding == null || understanding.getAnsweredSlots() == null || understanding.getAnsweredSlots().isEmpty()) {
            return false;
        }
        return understanding.getAnsweredSlots().stream().anyMatch(answered -> answered != null && answered.getSlot() == slotCode);
    }

    boolean hasPrimaryComplaintUnderstanding(TriageContext context) {
        if (context == null) {
            return false;
        }
        TurnUnderstanding understanding = context.getLatestTurnUnderstanding();
        if (understanding == null) {
            return false;
        }
        ComplaintUnderstanding complaint = understanding.getPrimaryComplaint();
        return complaint != null && StrUtil.isNotBlank(complaint.getValue());
    }
}
