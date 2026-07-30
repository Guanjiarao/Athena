

package com.nageoffer.ai.ragent.triage.risk;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Component
final class LegacyRiskFallback {

    static final String GOVERNANCE_TAG = "LEGACY_FALLBACK";

    RiskLevel evaluate(TriageContext context, Supplier<String> combinedTextSupplier) {
        boolean abdominalPain = hasPrimarySymptom(context, "腹痛")
                || containsAny(combinedTextSupplier.get(), List.of("腹痛", "肚子疼", "肚子痛"));
        boolean rightLowerQuadrantPain = hasBodyPart(context, "右下腹")
                || containsAny(combinedTextSupplier.get(), List.of("右下腹", "右下角痛", "右下角"));
        boolean vomitingKnown = isResolved(context.getSlotState(), SlotCode.VOMITING_PRESENCE);
        boolean chestPainOnly = hasRiskSignal(context, RiskSignalType.CHEST_PAIN)
                || hasResolvedPrimarySymptom(context, "胸痛")
                || containsAny(combinedTextSupplier.get(), List.of("胸痛", "胸口痛", "心口痛"));

        if (abdominalPain && rightLowerQuadrantPain && !vomitingKnown) {
            return RiskLevel.builder()
                    .level(2)
                    .score(55D)
                    .evidence("当前已知右下腹痛，仍缺少是否呕吐等关键伴随症状。")
                    .rationale("未命中硬红旗，宜继续补问关键伴随症状后再做进一步风险判断。")
                    .shouldInterrupt(Boolean.FALSE)
                    .needsMoreInfo(Boolean.TRUE)
                    .missingCriticalSlots(List.of(SlotCode.VOMITING_PRESENCE))
                    .riskHints(List.of("RIGHT_LOWER_QUADRANT_ABDOMINAL_PAIN"))
                    .build()
                    .normalize();
        }

        if (chestPainOnly) {
            return RiskLevel.builder()
                    .level(3)
                    .score(82D)
                    .evidence("胸痛属于需要优先排查的高危主诉。")
                    .rationale("虽然未命中硬红旗组合，但胸痛仍需优先线下评估。")
                    .shouldInterrupt(Boolean.TRUE)
                    .needsMoreInfo(Boolean.FALSE)
                    .riskHints(List.of("CHEST_PAIN"))
                    .build()
                    .normalize();
        }

        return RiskLevel.builder()
                .level(1)
                .score(20D)
                .evidence("当前描述未命中高风险红旗信号，也未命中特定高优先级 fallback。")
                .rationale("普通灰区风险应优先由结构化 risk 主链和风险分层结果决定；在未命中特定 fallback 时，legacy heuristic 不再提升主判断级别。")
                .shouldInterrupt(Boolean.FALSE)
                .needsMoreInfo(Boolean.FALSE)
                .build()
                .normalize();
    }

    private boolean hasPrimarySymptom(TriageContext context, String expectedValue) {
        if (context == null || StrUtil.isBlank(expectedValue)) {
            return false;
        }
        SlotState slotState = context.getSlotState();
        if (!isResolved(slotState, SlotCode.PRIMARY_SYMPTOM)) {
            return false;
        }
        SlotValue slotValue = slotState.get(SlotCode.PRIMARY_SYMPTOM);
        return slotValue != null && expectedValue.equals(slotValue.getValue());
    }

    private boolean hasResolvedPrimarySymptom(TriageContext context, String expectedValue) {
        return hasPrimarySymptom(context, expectedValue);
    }

    private boolean hasBodyPart(TriageContext context, String expectedValue) {
        if (context == null || StrUtil.isBlank(expectedValue)) {
            return false;
        }
        SlotState slotState = context.getSlotState();
        if (!isResolved(slotState, SlotCode.BODY_PART)) {
            return false;
        }
        SlotValue slotValue = slotState.get(SlotCode.BODY_PART);
        return slotValue != null && expectedValue.equals(slotValue.getValue());
    }

    private boolean hasRiskSignal(TriageContext context, RiskSignalType riskSignalType) {
        return context != null
                && context.getRiskSignalState() != null
                && context.getRiskSignalState().stream().anyMatch(each -> each != null
                && each.getType() == riskSignalType
                && each.getAssertion() == AssertionStatus.PRESENT);
    }

    private boolean isResolved(SlotState slotState, SlotCode slotCode) {
        if (slotState == null || slotCode == null) {
            return false;
        }
        SlotValue slotValue = slotState.get(slotCode);
        return slotValue != null && isResolved(slotValue.getStatus()) && StrUtil.isNotBlank(slotValue.getValue());
    }

    private boolean isResolved(SlotStatus slotStatus) {
        return slotStatus == SlotStatus.FILLED
                || slotStatus == SlotStatus.NEGATED
                || slotStatus == SlotStatus.CORRECTED
                || slotStatus == SlotStatus.INFERRED;
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
