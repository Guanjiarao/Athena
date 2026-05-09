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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComplaintFallbackResolverCompatibilityFallbackTest {

    @Test
    void shouldKeepHighConfidenceOneHopComplaintFallbacks() {
        ComplaintFallbackResolver resolver = new ComplaintFallbackResolver();

        assertEquals("腹痛", resolver.resolvePrimaryComplaint("肚子疼一天了"));
        assertEquals("胸痛", resolver.resolvePrimaryComplaint("胸口痛，有点压着疼"));
        assertEquals("胸部不适", resolver.resolvePrimaryComplaint("胸口有点不舒服"));
        assertEquals("发热", resolver.resolvePrimaryComplaint("今天一直发烧"));
        assertEquals("发热", resolver.resolvePrimaryComplaint("一直发热"));
    }

    @Test
    void shouldDropBodyCueDerivedAbdominalComplaintFallbacks() {
        ComplaintFallbackResolver resolver = new ComplaintFallbackResolver();

        assertNull(resolver.resolvePrimaryComplaint("胃疼"));
        assertNull(resolver.resolvePrimaryComplaint("胃痛"));
        assertNull(resolver.resolvePrimaryComplaint("肚子隐隐作痛"));
        assertNull(resolver.resolvePrimaryComplaint("肚子有点隐隐作痛"));
    }

    @Test
    void shouldDropOverBroadFeverFallbacks() {
        ComplaintFallbackResolver resolver = new ComplaintFallbackResolver();

        assertNull(resolver.resolvePrimaryComplaint("嗓子有点烧"));
        assertNull(resolver.resolvePrimaryComplaint("皮肤烧得慌"));
        assertNull(resolver.resolvePrimaryComplaint("今天不烧了"));
    }

    @Test
    void shouldKeepStructuredChestComplaintFallbacksNeededByRecoveredRiskLines() {
        ComplaintFallbackResolver resolver = new ComplaintFallbackResolver();

        assertNull(resolver.resolvePrimaryComplaint("不是胸口痛，是胃这边不舒服"));
        assertEquals("胸闷", resolver.resolvePrimaryComplaint("胸口闷"));
        assertEquals("胸闷", resolver.resolvePrimaryComplaint("胸闷"));
    }

    @Test
    void shouldRequireExplicitPainCueForWeakAbdominalFallback() {
        ComplaintFallbackResolver resolver = new ComplaintFallbackResolver();

        assertEquals("腹痛", resolver.resolveWeakSymptomWithBodyCue("肚子有点疼"));
        assertEquals("腹痛", resolver.resolveWeakSymptomWithBodyCue("胃有点作痛"));
        assertNull(resolver.resolveWeakSymptomWithBodyCue("肚子有点不舒服"));
        assertNull(resolver.resolveWeakSymptomWithBodyCue("胃有点难受"));
    }
}
