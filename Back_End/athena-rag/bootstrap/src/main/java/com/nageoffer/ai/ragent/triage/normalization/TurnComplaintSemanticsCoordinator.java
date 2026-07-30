

package com.nageoffer.ai.ragent.triage.normalization;

import com.nageoffer.ai.ragent.triage.model.ComplaintUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import com.nageoffer.ai.ragent.triage.slot.AnsweredSlotFlowCollector;
import com.nageoffer.ai.ragent.triage.slot.AnsweredSlotSemanticsAssembler;

import java.util.ArrayList;

public class TurnComplaintSemanticsCoordinator {
    private final ComplaintFallbackResolver complaintFallbackResolver;
    private final AnsweredSlotFlowCollector answeredSlotFlowCollector;
    private final AnsweredSlotSemanticsAssembler answeredSlotSemanticsAssembler;

    public TurnComplaintSemanticsCoordinator(ComplaintFallbackResolver complaintFallbackResolver,
                                             AnsweredSlotFlowCollector answeredSlotFlowCollector,
                                             AnsweredSlotSemanticsAssembler answeredSlotSemanticsAssembler) {
        this.complaintFallbackResolver = complaintFallbackResolver;
        this.answeredSlotFlowCollector = answeredSlotFlowCollector;
        this.answeredSlotSemanticsAssembler = answeredSlotSemanticsAssembler;
    }

    public void enrich(TriageContext context, TurnUnderstanding understanding, String text) {
        if (understanding == null) {
            return;
        }

        // 调试日志：记录 enrich 前的状态
        int slotsBeforeInit = understanding.getAnsweredSlots() == null ? -1 : understanding.getAnsweredSlots().size();

        if (understanding.getAnsweredSlots() == null) understanding.setAnsweredSlots(new ArrayList<>());
        if (understanding.getCorrections() == null) understanding.setCorrections(new ArrayList<>());
        if (understanding.getRiskSignals() == null) understanding.setRiskSignals(new ArrayList<>());

        // 调试日志：记录初始化后的状态
        int slotsAfterInit = understanding.getAnsweredSlots().size();

        ComplaintUnderstanding explicitComplaint = explicitComplaint(text);
        if (isBlankComplaint(understanding.getPrimaryComplaint())) {
            understanding.setPrimaryComplaint(firstNonBlankComplaint(explicitComplaint, complaintFromContext(context)));
        }

        // 调试日志：记录 collectInto 前的状态
        int slotsBeforeCollect = understanding.getAnsweredSlots().size();

        answeredSlotFlowCollector.collectInto(context, understanding, text);

        // 调试日志：记录 collectInto 后的状态
        int slotsAfterCollect = understanding.getAnsweredSlots().size();
        System.out.println(String.format("[DEBUG] enrich: before_init=%d, after_init=%d, before_collect=%d, after_collect=%d",
            slotsBeforeInit, slotsAfterInit, slotsBeforeCollect, slotsAfterCollect));

        answeredSlotSemanticsAssembler.apply(context, understanding, text, explicitComplaint);
    }

    private ComplaintUnderstanding explicitComplaint(String text) {
        String value = complaintFallbackResolver.resolvePrimaryComplaint(text);
        if (blank(value)) value = complaintFallbackResolver.resolveWeakSymptomWithBodyCue(text);
        return blank(value) ? null : ComplaintUnderstanding.builder().value(value).confidence(0.8D).evidence(text).build();
    }

    private ComplaintUnderstanding complaintFromContext(TriageContext context) {
        if (context == null) {
            return null;
        }
        SlotValue primarySymptom = context.getSlotState() == null ? null : context.getSlotState().get(SlotCode.PRIMARY_SYMPTOM);
        if (primarySymptom != null && !blank(primarySymptom.getValue())) {
            return ComplaintUnderstanding.builder()
                    .value(primarySymptom.getValue().trim())
                    .confidence(0.7D)
                    .evidence(primarySymptom.getEvidence())
                    .build();
        }
        if (!blank(context.getFinalPrimaryComplaint())) {
            return ComplaintUnderstanding.builder()
                    .value(context.getFinalPrimaryComplaint().trim())
                    .confidence(0.7D)
                    .evidence("context.finalPrimaryComplaint")
                    .build();
        }
        return null;
    }

    private ComplaintUnderstanding firstNonBlankComplaint(ComplaintUnderstanding first, ComplaintUnderstanding second) {
        return isBlankComplaint(first) ? second : first;
    }

    private boolean isBlankComplaint(ComplaintUnderstanding complaint) {
        return complaint == null || blank(complaint.getValue());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
