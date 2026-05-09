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

import com.nageoffer.ai.ragent.triage.model.AskabilityDecision;
import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.RiskDecision;
import com.nageoffer.ai.ragent.triage.model.RiskDecisionType;
import com.nageoffer.ai.ragent.triage.model.RiskGap;
import com.nageoffer.ai.ragent.triage.model.RiskSignalStatus;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPlannerRiskContractTest {

    @Test
    void shouldPrioritizeUnresolvedRiskGapOverPrimaryComplaintGapWhenComplaintAlreadyExists() {
        QuestionPlanner planner = new QuestionPlanner(new QuestionPlanSupport());
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("胸部不适").status(SlotStatus.FILLED).build());
        TriageContext context = TriageContext.builder()
                .slotState(slotState)
                .riskSignalState(List.of(RiskSignalUnderstanding.builder()
                        .type(RiskSignalType.DYSPNEA)
                        .assertion(AssertionStatus.SUSPECTED)
                        .build()))
                .riskDecision(RiskDecision.builder()
                        .decisionType(RiskDecisionType.ASK_RISK_CLARIFICATION)
                        .needsMoreInfo(Boolean.TRUE)
                        .unresolvedRiskGaps(List.of(RiskGap.builder()
                                .slot(SlotCode.DYSPNEA_PRESENCE)
                                .relatedSignalType(RiskSignalType.DYSPNEA)
                                .signalStatus(RiskSignalStatus.UNRESOLVED)
                                .priority(95)
                                .reason("risk gap")
                                .build()))
                        .build())
                .build();
        context.ensureCollections();

        planner.execute(context);

        assertEquals(List.of(SlotCode.DYSPNEA_PRESENCE), context.getPendingSlots());
        assertTrue(context.getSelectedQuestionGaps().stream().map(QuestionGap::getSlot)
                .anyMatch(slot -> slot == SlotCode.DYSPNEA_PRESENCE));
        assertTrue(context.getSuppressedQuestionGaps().stream().map(QuestionGap::getSlot)
                .noneMatch(slot -> slot == SlotCode.PRIMARY_SYMPTOM));
    }

    @Test
    void shouldKeepUnknownRiskAnswerAskableForRepeatClarification() {
        QuestionPlanner planner = new QuestionPlanner(new QuestionPlanSupport());
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("胸部不适").status(SlotStatus.FILLED).build());
        slotState.put(SlotValue.builder().slot(SlotCode.DYSPNEA_PRESENCE).value("UNKNOWN").status(SlotStatus.UNKNOWN).build());
        TriageContext context = TriageContext.builder()
                .slotState(slotState)
                .riskDecision(RiskDecision.builder()
                        .decisionType(RiskDecisionType.ASK_RISK_CLARIFICATION)
                        .needsMoreInfo(Boolean.TRUE)
                        .unresolvedRiskGaps(List.of(RiskGap.builder()
                                .slot(SlotCode.DYSPNEA_PRESENCE)
                                .relatedSignalType(RiskSignalType.DYSPNEA)
                                .signalStatus(RiskSignalStatus.UNRESOLVED)
                                .priority(95)
                                .reason("risk gap")
                                .build()))
                        .build())
                .build();
        context.ensureCollections();

        planner.execute(context);

        assertTrue(context.getAskabilityDecisions().stream()
                .filter(decision -> decision.getSlot() == SlotCode.DYSPNEA_PRESENCE)
                .map(AskabilityDecision::getAskable)
                .findFirst()
                .orElse(Boolean.FALSE));
        assertEquals(List.of(SlotCode.DYSPNEA_PRESENCE), context.getPendingSlots());
    }
}
