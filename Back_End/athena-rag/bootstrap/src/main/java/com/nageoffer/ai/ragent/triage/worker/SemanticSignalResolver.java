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
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageContext;

final class SemanticSignalResolver {

    static final String GOVERNANCE_TAG = HeuristicGovernanceTags.COMPATIBILITY_ONLY;

    boolean hasPrimarySignalFact(TriageContext context, String semanticSignal) {
        if (context == null || semanticSignal == null || semanticSignal.isBlank() || context.getFactHistory() == null) {
            return false;
        }
        for (Fact fact : context.getFactHistory()) {
            if (fact == null || fact.getSlot() == null || fact.getCanonicalValue() == null) {
                continue;
            }
            if (fact.getSlot() == SlotCode.PRIMARY_SYMPTOM && semanticSignal.equals(fact.getCanonicalValue())) {
                return true;
            }
        }
        return false;
    }
}
