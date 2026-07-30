

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
