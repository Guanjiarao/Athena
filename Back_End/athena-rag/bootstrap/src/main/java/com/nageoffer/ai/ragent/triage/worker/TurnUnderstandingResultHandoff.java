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

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnIntent;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.function.Supplier;

public class TurnUnderstandingResultHandoff {
    private final TurnUnderstandingPostProcessor turnUnderstandingPostProcessor;

    public TurnUnderstandingResultHandoff(TurnUnderstandingPostProcessor turnUnderstandingPostProcessor) {
        this.turnUnderstandingPostProcessor = turnUnderstandingPostProcessor;
    }

    public TurnUnderstanding handoff(TriageContext context,
                                     String latestTurn,
                                     Supplier<TurnUnderstanding> parsedResultSupplier) {
        TurnUnderstanding fallback = buildFallback();
        TurnUnderstanding understanding;
        try {
            understanding = parsedResultSupplier == null ? fallback : parsedResultSupplier.get();
        } catch (Exception ignored) {
            understanding = fallback;
        }
        return turnUnderstandingPostProcessor.normalizeAndEnrich(context, understanding, fallback, latestTurn);
    }

    private TurnUnderstanding buildFallback() {
        return TurnUnderstanding.builder().intent(TurnIntent.UNKNOWN).confidence(0.0D).build();
    }
}
