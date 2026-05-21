

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 本轮语义归一化结果。
 *
 * <p>Normalization 边界只表达“用户本轮说了什么”：TurnUnderstanding、主诉、语义信号、症状和
 * normalized facts。这里的 facts 是本轮新证据/事实流，不直接代表跨轮 Slot 状态；Slot 模块消费
 * facts 后再产出 slot patch。</p>
 */
@Data
@Builder
public class NormalizedTurn {

    private TurnUnderstanding turnUnderstanding;

    private String primaryComplaint;

    @Builder.Default
    private List<String> signals = new ArrayList<>();

    @Builder.Default
    private List<Symptom> symptoms = new ArrayList<>();

    /**
     * 归一化后的事实增量。Fact 仍保留 slot 指向，但该指向只是给 SlotAgent 的候选输入，
     * 不等同于已写入的 SlotState。
     */
    @Builder.Default
    private List<Fact> facts = new ArrayList<>();

    /**
     * 本轮 facts 明确提及的槽位，仅用于说明“候选已回答槽位”，最终是否写入由 SlotAgent 决定。
     */
    @Builder.Default
    private List<SlotCode> answeredSlots = new ArrayList<>();
}
