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

public class TurnUnderstandingPostProcessor {
    private final TurnComplaintSemanticsCoordinator turnComplaintSemanticsCoordinator;

    public TurnUnderstandingPostProcessor(TurnComplaintSemanticsCoordinator turnComplaintSemanticsCoordinator) {
        this.turnComplaintSemanticsCoordinator = turnComplaintSemanticsCoordinator;
    }

    public TurnUnderstanding normalizeAndEnrich(TriageContext context,
                                                TurnUnderstanding understanding,
                                                TurnUnderstanding fallback,
                                                String latestTurn) {
        TurnUnderstanding normalized = understanding == null ? fallback : understanding;
        if (normalized == null) {
            normalized = TurnUnderstanding.builder().intent(TurnIntent.UNKNOWN).confidence(0.0D).build();
        }
        if (normalized.getIntent() == null) {
            normalized.setIntent(TurnIntent.UNKNOWN);
        }
        turnComplaintSemanticsCoordinator.enrich(context, normalized, latestTurn);
        return normalized;
    }
}
