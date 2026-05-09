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

import com.nageoffer.ai.ragent.triage.model.QuestionGap;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPlanSupportCanonicalStatePriorityTest {

    @Test
    void shouldDiscoverAbdominalPainFollowUpGapsFromCanonicalPrimarySymptom() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("腹痛").status(SlotStatus.FILLED).build());
        TriageContext context = TriageContext.builder().slotState(slotState).build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.BODY_PART));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.PAIN_SEVERITY));
        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.FEVER_PRESENCE));
    }

    @Test
    void shouldDiscoverTemperatureGapFromCanonicalPrimarySymptomFever() {
        QuestionPlanSupport support = new QuestionPlanSupport();
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder().slot(SlotCode.PRIMARY_SYMPTOM).value("发热").status(SlotStatus.FILLED).build());
        TriageContext context = TriageContext.builder().slotState(slotState).build();
        context.ensureCollections();

        List<QuestionGap> gaps = support.determineQuestionGaps(context);

        assertTrue(gaps.stream().anyMatch(gap -> gap.getSlot() == SlotCode.TEMPERATURE));
    }
}
