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

import com.nageoffer.ai.ragent.triage.model.AnsweredSlotUnderstanding;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SlotAnswerInferenceHelperTest {

    @Test
    void shouldInferDurationFromSpannedNaturalExpression() {
        SlotAnswerInferenceHelper helper = new SlotAnswerInferenceHelper(new ComplaintFallbackResolver());

        AnsweredSlotUnderstanding answered = helper.infer(SlotCode.DURATION, "我从昨天晚上发烧到现在还肚子疼");

        assertNotNull(answered);
        assertEquals(SlotCode.DURATION, answered.getSlot());
        assertEquals("昨天晚上到现在", answered.getNormalizedValue());
    }

    @Test
    void shouldInferDurationFromShortOnsetExpression() {
        SlotAnswerInferenceHelper helper = new SlotAnswerInferenceHelper(new ComplaintFallbackResolver());

        AnsweredSlotUnderstanding answered = helper.infer(SlotCode.DURATION, "昨天晚上开始发烧了");

        assertNotNull(answered);
        assertEquals("昨天晚上开始", answered.getNormalizedValue());
    }
}
