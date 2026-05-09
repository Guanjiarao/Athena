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

import com.nageoffer.ai.ragent.triage.model.Symptom;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticSymptomHeuristicHelperCompatibilityFallbackTest {

    @Test
    void shouldPreferGenericDiscomfortWhenOnlyWeakAbdominalDiscomfortExists() {
        SemanticSymptomHeuristicHelper helper = new SemanticSymptomHeuristicHelper(new ComplaintFallbackResolver());

        List<Symptom> symptoms = helper.heuristicExtract("肚子有点不舒服");

        assertEquals(1, symptoms.size());
        assertEquals("不适", symptoms.get(0).getName());
    }

    @Test
    void shouldStillInferAbdominalPainWhenExplicitPainCueExists() {
        SemanticSymptomHeuristicHelper helper = new SemanticSymptomHeuristicHelper(new ComplaintFallbackResolver());

        List<Symptom> symptoms = helper.heuristicExtract("肚子有点疼");

        assertEquals(1, symptoms.size());
        assertEquals("腹痛", symptoms.get(0).getName());
    }

    @Test
    void shouldStillRecognizeStomachPainViaSymptomKeywordsWithoutPrimaryComplaintFallback() {
        SemanticSymptomHeuristicHelper helper = new SemanticSymptomHeuristicHelper(new ComplaintFallbackResolver());

        List<Symptom> symptoms = helper.heuristicExtract("胃疼两天了");

        assertEquals(1, symptoms.size());
        assertEquals("腹痛", symptoms.get(0).getName());
    }
}
