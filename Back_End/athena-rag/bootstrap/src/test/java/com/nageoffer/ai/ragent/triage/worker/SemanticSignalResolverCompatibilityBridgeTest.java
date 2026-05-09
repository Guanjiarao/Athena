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

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.FactType;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticSignalResolverCompatibilityBridgeTest {

    @Test
    void shouldKeepPrimarySymptomFactAsNarrowCompatibilityBridge() {
        SemanticSignalResolver resolver = new SemanticSignalResolver();
        TriageContext context = TriageContext.builder()
                .factHistory(List.of(Fact.builder()
                        .type(FactType.PRIMARY_SIGNAL)
                        .slot(SlotCode.PRIMARY_SYMPTOM)
                        .canonicalValue("胸痛")
                        .polarity(FactPolarity.POSITIVE)
                        .build()))
                .build();
        context.ensureCollections();

        assertTrue(resolver.hasPrimarySignalFact(context, "胸痛"));
    }

    @Test
    void shouldNotInferSemanticSignalFromNonCanonicalFactBridge() {
        SemanticSignalResolver resolver = new SemanticSignalResolver();
        TriageContext feverContext = TriageContext.builder()
                .factHistory(List.of(Fact.builder()
                        .type(FactType.FOLLOW_UP_ANSWER)
                        .slot(SlotCode.FEVER_PRESENCE)
                        .canonicalValue("YES")
                        .polarity(FactPolarity.POSITIVE)
                        .build()))
                .build();
        feverContext.ensureCollections();

        TriageContext chestBodyPartContext = TriageContext.builder()
                .factHistory(List.of(Fact.builder()
                        .type(FactType.SLOT_EVIDENCE)
                        .slot(SlotCode.BODY_PART)
                        .canonicalValue("胸前区")
                        .polarity(FactPolarity.NEUTRAL)
                        .build()))
                .build();
        chestBodyPartContext.ensureCollections();

        assertFalse(resolver.hasPrimarySignalFact(feverContext, "发热"));
        assertFalse(resolver.hasPrimarySignalFact(chestBodyPartContext, "胸痛"));
    }
}
