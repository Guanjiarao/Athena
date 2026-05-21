

package com.nageoffer.ai.ragent.triage.slot;

import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slot Agent 输出。
 *
 * <p>slotPatch 是 Slot 模块根据 normalized facts、上一轮 SlotState 和纠错语义归约出的状态增量/快照，
 * 是跨轮结构化槽位状态的唯一对外结果；它不同于 NormalizedTurn.facts，后者只是本轮语义证据。</p>
 */
@Data
@Builder
public class SlotAgentResult {

    @Builder.Default
    private Map<SlotCode, SlotValue> slotPatch = new LinkedHashMap<>();

    @Builder.Default
    private List<SlotCode> answeredSlots = new ArrayList<>();
}
