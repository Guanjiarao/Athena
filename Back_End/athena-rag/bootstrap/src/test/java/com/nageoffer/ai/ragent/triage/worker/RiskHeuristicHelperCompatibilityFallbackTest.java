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

import com.nageoffer.ai.ragent.triage.model.AssertionStatus;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.RiskSignalType;
import com.nageoffer.ai.ragent.triage.model.RiskSignalUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskHeuristicHelperCompatibilityFallbackTest {

    @Test
    void shouldKeepRlqUnresolvedFallback() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder()
                .slot(SlotCode.PRIMARY_SYMPTOM)
                .value("腹痛")
                .status(SlotStatus.FILLED)
                .updatedAt(Instant.now())
                .build());
        slotState.put(SlotValue.builder()
                .slot(SlotCode.BODY_PART)
                .value("右下腹")
                .status(SlotStatus.FILLED)
                .updatedAt(Instant.now())
                .build());
        TriageContext context = TriageContext.builder()
                .userInput("右下腹痛")
                .slotState(slotState)
                .build();
        context.ensureCollections();

        RiskLevel level = helper.heuristicRiskFallback(context);

        assertNotNull(level);
        assertEquals(2, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getNeedsMoreInfo()));
        assertTrue(level.getMissingCriticalSlots().contains(SlotCode.VOMITING_PRESENCE));
    }

    @Test
    void shouldUseCanonicalStateForRlqFallbackEvenWhenUserInputIsSparse() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder()
                .slot(SlotCode.PRIMARY_SYMPTOM)
                .value("腹痛")
                .status(SlotStatus.FILLED)
                .updatedAt(Instant.now())
                .build());
        slotState.put(SlotValue.builder()
                .slot(SlotCode.BODY_PART)
                .value("右下腹")
                .status(SlotStatus.FILLED)
                .updatedAt(Instant.now())
                .build());
        TriageContext context = TriageContext.builder()
                .userInput("有点疼")
                .slotState(slotState)
                .build();
        context.ensureCollections();

        RiskLevel level = helper.legacyRiskFallback(context);

        assertNotNull(level);
        assertEquals(2, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getNeedsMoreInfo()));
    }

    @Test
    void shouldKeepChestPainOnlyFallback() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        TriageContext context = TriageContext.builder()
                .userInput("胸口痛")
                .slotState(SlotState.empty())
                .build();
        context.ensureCollections();

        RiskLevel level = helper.legacyRiskFallback(context);

        assertNotNull(level);
        assertEquals(3, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getShouldInterrupt()));
    }

    @Test
    void shouldUseChestPainRiskSignalForLegacyFallbackEvenWithoutDirectChestText() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        TriageContext context = TriageContext.builder()
                .userInput("人不太舒服")
                .slotState(SlotState.empty())
                .riskSignalState(List.of(
                        RiskSignalUnderstanding.builder().type(RiskSignalType.CHEST_PAIN).assertion(AssertionStatus.PRESENT).build()
                ))
                .build();
        context.ensureCollections();

        RiskLevel level = helper.legacyRiskFallback(context);

        assertNotNull(level);
        assertEquals(3, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getShouldInterrupt()));
    }

    @Test
    void shouldDelegateHeuristicFallbackToHardRedFlagFirstThenLegacyFallback() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();

        TriageContext hardFlagContext = TriageContext.builder()
                .userInput("胸口痛，喘不过气")
                .slotState(SlotState.empty())
                .build();
        hardFlagContext.ensureCollections();
        RiskLevel hardFlagLevel = helper.heuristicRiskFallback(hardFlagContext);
        assertEquals(4, hardFlagLevel.getLevel());

        TriageContext legacyFallbackContext = TriageContext.builder()
                .userInput("胸口痛")
                .slotState(SlotState.empty())
                .build();
        legacyFallbackContext.ensureCollections();
        RiskLevel legacyFallbackLevel = helper.heuristicRiskFallback(legacyFallbackContext);
        assertEquals(3, legacyFallbackLevel.getLevel());
    }

    @Test
    void shouldUseResolvedYesSlotsForPregnancyBleedingHardGuardrail() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PREGNANCY_STATUS).value("YES").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        slotState.put(SlotValue.builder().slot(SlotCode.BLEEDING_PRESENCE).value("YES").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        TriageContext context = TriageContext.builder()
                .userInput("有点不舒服")
                .slotState(slotState)
                .build();
        context.ensureCollections();

        RiskLevel level = helper.hardRedFlagFallback(context);

        assertNotNull(level);
        assertEquals(4, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getShouldInterrupt()));
    }

    @Test
    void shouldUseRiskSignalsForChestPainWithDyspneaHardGuardrailEvenWithoutStrongText() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        TriageContext context = TriageContext.builder()
                .userInput("人很难受")
                .slotState(SlotState.empty())
                .riskSignalState(List.of(
                        RiskSignalUnderstanding.builder().type(RiskSignalType.CHEST_PAIN).assertion(AssertionStatus.PRESENT).build(),
                        RiskSignalUnderstanding.builder().type(RiskSignalType.DYSPNEA).assertion(AssertionStatus.PRESENT).build()
                ))
                .build();
        context.ensureCollections();

        RiskLevel level = helper.hardRedFlagFallback(context);

        assertNotNull(level);
        assertEquals(4, level.getLevel());
        assertTrue(Boolean.TRUE.equals(level.getShouldInterrupt()));
    }

    @Test
    void shouldNoLongerEscalateGenericModerateLoadFallback() {
        RiskHeuristicHelper helper = new RiskHeuristicHelper();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("腹痛").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        slotState.put(SlotValue.builder().slot(SlotCode.DURATION).value("一天").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        slotState.put(SlotValue.builder().slot(SlotCode.BODY_PART).value("上腹").status(SlotStatus.FILLED).updatedAt(Instant.now()).build());
        TriageContext context = TriageContext.builder()
                .userInput("肚子不舒服一天了")
                .slotState(slotState)
                .extractedSymptoms(List.of(
                        Symptom.builder().name("腹痛").severity("中度").build(),
                        Symptom.builder().name("恶心").build(),
                        Symptom.builder().name("乏力").build()
                ))
                .build();
        context.ensureCollections();

        RiskLevel level = helper.heuristicRiskFallback(context);

        assertNotNull(level);
        assertEquals(1, level.getLevel());
        assertFalse(Boolean.TRUE.equals(level.getShouldInterrupt()));
        assertFalse(Boolean.TRUE.equals(level.getNeedsMoreInfo()));
    }
}
