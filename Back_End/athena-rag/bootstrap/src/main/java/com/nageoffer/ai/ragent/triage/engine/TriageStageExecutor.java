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

package com.nageoffer.ai.ragent.triage.engine;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.worker.FactExtractor;
import com.nageoffer.ai.ragent.triage.worker.QuestionPlanner;
import com.nageoffer.ai.ragent.triage.worker.SOPValidatorWorker;
import com.nageoffer.ai.ragent.triage.worker.SemanticParserWorker;
import com.nageoffer.ai.ragent.triage.worker.SlotManager;
import com.nageoffer.ai.ragent.triage.worker.StateReducer;
import com.nageoffer.ai.ragent.triage.worker.TurnUnderstandingWorker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class TriageStageExecutor {

    private TriageStageExecutor() {
    }

    static String executeParsing(TriageContext context,
                                 TurnUnderstandingWorker turnUnderstandingWorker,
                                 SemanticParserWorker semanticParserWorker,
                                 FactExtractor factExtractor,
                                 StateReducer stateReducer,
                                 SlotManager slotManager) {
        turnUnderstandingWorker.execute(context);
        semanticParserWorker.execute(context);
        factExtractor.execute(context);
        stateReducer.execute(context);
        slotManager.execute(context);
        int symptomCount = context.getExtractedSymptoms() == null ? 0 : context.getExtractedSymptoms().size();
        int factCount = context.getFactHistory() == null ? 0 : context.getFactHistory().size();
        int filledSlotCount = context.getSlotState() == null || context.getSlotState().getSlots() == null
                ? 0
                : context.getSlotState().getSlots().size();
        int reducedSlotCount = context.getLatestStateReducerResult() == null || context.getLatestStateReducerResult().getReducedSlots() == null
                ? 0
                : context.getLatestStateReducerResult().getReducedSlots().size();
        String turnIntent = context.getLatestTurnUnderstanding() == null || context.getLatestTurnUnderstanding().getIntent() == null
                ? "UNKNOWN"
                : context.getLatestTurnUnderstanding().getIntent().name();
        return "Parsing finished with intent=" + turnIntent + ", " + symptomCount + " symptom(s), "
                + factCount + " fact(s), " + reducedSlotCount + " reduced slot(s), and " + filledSlotCount + " projected slot value(s).";
    }

    static String executeValidation(TriageContext context,
                                    QuestionPlanner questionPlanner,
                                    SOPValidatorWorker sopValidatorWorker) {
        questionPlanner.execute(context);
        syncMissingFieldsFromQuestionPlan(context);
        if (shouldRunLegacyValidationFallback(context)) {
            List<String> plannedMissingFields = new ArrayList<>(context.getMissingFields());
            sopValidatorWorker.execute(context);
            context.setMissingFields(mergeMissingFields(plannedMissingFields, context.getMissingFields()));
        }
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null) {
            context.setLastAskedSlots(new ArrayList<>(questionPlan.getNextSlotsToAsk()));
        }
        if (context.hasMissingFields()) {
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            context.setFinalReply(TriageReplyBuilder.buildClarificationReply(context));
            return buildPendingSlotRationale(context);
        }
        return "All mandatory slot requirements are complete.";
    }

    private static void syncMissingFieldsFromQuestionPlan(TriageContext context) {
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan == null || questionPlan.getPendingSlots() == null || questionPlan.getPendingSlots().isEmpty()) {
            context.setMissingFields(new ArrayList<>());
            return;
        }
        List<String> missingFields = new ArrayList<>();
        for (SlotCode slotCode : questionPlan.getPendingSlots()) {
            String fieldName = mapSlotToMissingField(slotCode);
            if (fieldName != null) {
                missingFields.add(fieldName);
            }
        }
        context.setMissingFields(missingFields);
    }

    private static boolean shouldRunLegacyValidationFallback(TriageContext context) {
        return context.getQuestionPlan() == null;
    }

    private static String buildPendingSlotRationale(TriageContext context) {
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (!pendingSlots.isEmpty()) {
            String priorityReason = questionPlan == null ? null : questionPlan.getPriorityReason();
            String slotSummary = pendingSlots.stream()
                    .map(TriageStageExecutor::mapSlotToMissingField)
                    .filter(each -> each != null && !each.isBlank())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("关键槽位");
            if (priorityReason != null && !priorityReason.isBlank()) {
                return "Pending slots: " + slotSummary + ". " + priorityReason;
            }
            return "Pending slots: " + slotSummary + ".";
        }
        List<String> missingFields = context.getMissingFields() == null ? List.of() : context.getMissingFields();
        if (!missingFields.isEmpty()) {
            return "Compatibility fallback missing fields: " + String.join(", ", missingFields);
        }
        return "Additional clarification is required before risk assessment.";
    }

    private static List<String> mergeMissingFields(List<String> primaryFields, List<String> fallbackFields) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (primaryFields != null) {
            merged.addAll(primaryFields);
        }
        if (fallbackFields != null) {
            merged.addAll(fallbackFields);
        }
        return new ArrayList<>(merged);
    }

    private static String mapSlotToMissingField(SlotCode slotCode) {
        if (slotCode == null) {
            return null;
        }
        return switch (slotCode) {
            case PRIMARY_SYMPTOM -> "主诉症状";
            case DURATION -> "持续时间";
            case BODY_PART -> "疼痛部位";
            case PAIN_CHARACTER -> "疼痛性质";
            case PAIN_SEVERITY -> "疼痛程度";
            case FEVER_PRESENCE -> "是否伴随发热";
            case TEMPERATURE -> "体温";
            case NAUSEA_PRESENCE -> "是否伴随恶心";
            case VOMITING_PRESENCE -> "是否伴随呕吐";
            case DYSPNEA_PRESENCE -> "是否伴随呼吸困难";
            case BLEEDING_PRESENCE -> "是否伴随出血";
            case PREGNANCY_STATUS -> "是否妊娠";
            case SEIZURE_PRESENCE -> "是否存在抽搐";
            case DIARRHEA_PRESENCE -> "是否伴随腹泻";
        };
    }
}
