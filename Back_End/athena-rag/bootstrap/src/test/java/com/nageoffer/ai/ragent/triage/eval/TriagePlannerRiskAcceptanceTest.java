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

package com.nageoffer.ai.ragent.triage.eval;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskDecisionType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriagePlannerRiskAcceptanceTest {

    @Test
    void shouldNotReaskAnsweredDurationAndBodyPart() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP5-001",
                List.of("肚子右下腹痛一天了，有点恶心，没有发热"),
                Map.of(
                        "PRIMARY_SYMPTOM", slotSeed("腹痛", "FILLED"),
                        "DURATION", slotSeed("一天", "FILLED"),
                        "BODY_PART", slotSeed("右下腹", "FILLED")
                ),
                List.of(),
                List.of()
        ));

        QuestionPlan plan = context.getQuestionPlan();
        assertNotNull(plan);
        assertFalse(plan.getPendingSlots().contains(SlotCode.DURATION));
        assertFalse(plan.getPendingSlots().contains(SlotCode.BODY_PART));
    }

    @Test
    void shouldNotChaseOldValueAfterCorrection() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP5-002",
                List.of("不是左边，是右下腹痛一天了"),
                Map.of(
                        "PRIMARY_SYMPTOM", slotSeed("腹痛", "FILLED"),
                        "BODY_PART", slotSeed("右下腹", "CORRECTED")
                ),
                List.of(),
                List.of()
        ));

        QuestionPlan plan = context.getQuestionPlan();
        assertNotNull(plan);
        assertFalse(plan.getPendingSlots().contains(SlotCode.BODY_PART));
    }

    @Test
    void shouldCollapseAfterMultiSlotAnswerInSingleTurn() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP5-003",
                List.of("肚子右下腹痛一天了，没有发热，也没有呕吐"),
                Map.of(
                        "PRIMARY_SYMPTOM", slotSeed("腹痛", "FILLED"),
                        "DURATION", slotSeed("一天", "FILLED"),
                        "BODY_PART", slotSeed("右下腹", "FILLED"),
                        "FEVER_PRESENCE", slotSeed("NO", "NEGATED"),
                        "VOMITING_PRESENCE", slotSeed("NO", "NEGATED")
                ),
                List.of(),
                List.of()
        ));

        QuestionPlan plan = context.getQuestionPlan();
        assertNotNull(plan);
        assertFalse(plan.getPendingSlots().contains(SlotCode.DURATION));
        assertFalse(plan.getPendingSlots().contains(SlotCode.BODY_PART));
        assertFalse(plan.getPendingSlots().contains(SlotCode.FEVER_PRESENCE));
        assertFalse(plan.getPendingSlots().contains(SlotCode.VOMITING_PRESENCE));
    }

    @Test
    void shouldPrioritizeRiskClarificationOverRoutineFollowUp() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP5-004",
                List.of("胸口痛"),
                Map.of(
                        "PRIMARY_SYMPTOM", slotSeed("胸痛", "FILLED")
                ),
                List.of(),
                List.of()
        ));

        QuestionPlan plan = context.getQuestionPlan();
        assertNotNull(plan);
        assertFalse(plan.getNextSlotsToAsk().isEmpty());
        assertEquals(SlotCode.DYSPNEA_PRESENCE, plan.getNextSlotsToAsk().get(0));
    }

    @Test
    void shouldEscalateWhenNewRiskSignalAppearsOnSecondTurn() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP6-001",
                List.of("胸口有点痛", "现在开始喘不过气了"),
                Map.of(),
                List.of(),
                List.of()
        ));

        assertEquals(TriageAction.TRIGGER_WARNING, context.getNextAction());
        assertNotNull(context.getRiskDecision());
        assertEquals(RiskDecisionType.TRIGGER_WARNING, context.getRiskDecision().getDecisionType());
    }

    @Test
    void shouldTreatQuestionGapAsClarificationNotHardRisk() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP6-002",
                List.of("肚子疼"),
                Map.of(
                        "PRIMARY_SYMPTOM", slotSeed("腹痛", "FILLED")
                ),
                List.of("DURATION"),
                List.of("DURATION")
        ));

        assertEquals(TriageAction.ASK_CLARIFICATION, context.getNextAction());
        assertFalse(context.getFinalReply() == null || context.getFinalReply().isBlank());
    }

    @Test
    void shouldTriggerWarningForPregnancyBleedingCombination() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP6-003",
                List.of("我怀孕了，今天下面一直出血"),
                Map.of(),
                List.of(),
                List.of()
        ));

        assertEquals(TriageAction.TRIGGER_WARNING, context.getNextAction());
        assertNotNull(context.getRiskDecision());
        assertEquals(RiskDecisionType.TRIGGER_WARNING, context.getRiskDecision().getDecisionType());
        assertFalse(context.getRiskDecision().getConfirmedRiskGaps().isEmpty());
    }

    @Test
    void shouldTriggerWarningForConsciousnessColloquialVariant() {
        TriageContext context = execute(buildCase(
                "TRIAGE-STEP6-004",
                List.of("人都迷糊了，叫他也没什么反应"),
                Map.of(),
                List.of(),
                List.of()
        ));

        assertEquals(TriageAction.TRIGGER_WARNING, context.getNextAction());
        assertNotNull(context.getRiskDecision());
        assertEquals(RiskDecisionType.TRIGGER_WARNING, context.getRiskDecision().getDecisionType());
        assertFalse(context.getRiskDecision().getConfirmedRiskGaps().isEmpty());
    }

    private TriageContext execute(TriageEvalCase testCase) {
        TriageEvalRealExecutor executor = new TriageEvalRealExecutor(
                TriageEvalRealExecutor.heuristicLlmStub(),
                TriageEvalRealExecutor.stubGateway()
        );
        return executor.execute(testCase);
    }

    private TriageEvalCase buildCase(String id,
                                     List<String> turns,
                                     Map<String, TriageEvalCase.SlotSeed> slotState,
                                     List<String> lastAskedSlots,
                                     List<String> pendingSlots) {
        return TriageEvalCase.builder()
                .id(id)
                .category("acceptance")
                .priority("P0")
                .turns(turns.stream().map(text -> TriageEvalCase.Turn.builder().role("user").text(text).build()).toList())
                .context(TriageEvalCase.EvalContext.builder()
                        .slotState(slotState)
                        .lastAskedSlots(lastAskedSlots)
                        .pendingSlots(pendingSlots)
                        .build())
                .build();
    }

    private TriageEvalCase.SlotSeed slotSeed(String value, String status) {
        return TriageEvalCase.SlotSeed.builder().value(value).status(status).build();
    }
}
