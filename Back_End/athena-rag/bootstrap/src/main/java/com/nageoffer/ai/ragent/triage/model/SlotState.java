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

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Primary slot-based truth source for triage reasoning.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlotState {

    @Default
    private Map<SlotCode, SlotValue> slots = new EnumMap<>(SlotCode.class);

    public static SlotState empty() {
        return SlotState.builder().build();
    }

    public void ensureInitialized() {
        if (slots == null) {
            slots = new EnumMap<>(SlotCode.class);
        }
    }

    public SlotValue get(SlotCode slotCode) {
        ensureInitialized();
        return slotCode == null ? null : slots.get(slotCode);
    }

    public void put(SlotValue slotValue) {
        ensureInitialized();
        if (slotValue == null || slotValue.getSlot() == null) {
            return;
        }
        slots.put(slotValue.getSlot(), slotValue);
    }

    public boolean isFilled(SlotCode slotCode) {
        SlotValue slotValue = get(slotCode);
        return slotValue != null && slotValue.getStatus() == SlotStatus.FILLED;
    }

    public List<SlotCode> missingSlots(List<SlotCode> requiredSlots) {
        ensureInitialized();
        List<SlotCode> result = new ArrayList<>();
        if (requiredSlots == null || requiredSlots.isEmpty()) {
            return result;
        }
        for (SlotCode requiredSlot : requiredSlots) {
            SlotValue slotValue = get(requiredSlot);
            if (slotValue == null || slotValue.getStatus() != SlotStatus.FILLED) {
                result.add(requiredSlot);
            }
        }
        return result;
    }
}
