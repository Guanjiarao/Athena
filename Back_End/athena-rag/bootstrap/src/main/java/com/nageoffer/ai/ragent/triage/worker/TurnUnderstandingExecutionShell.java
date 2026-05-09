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
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;

import java.util.function.Supplier;

public class TurnUnderstandingExecutionShell {
    private final TurnUnderstandingResultHandoff turnUnderstandingResultHandoff;

    public TurnUnderstandingExecutionShell(TurnUnderstandingResultHandoff turnUnderstandingResultHandoff) {
        this.turnUnderstandingResultHandoff = turnUnderstandingResultHandoff;
    }

    public TriageContext execute(TriageContext context,
                                 String latestTurn,
                                 Supplier<TurnUnderstanding> parsedResultSupplier) {
        if (context == null) context = new TriageContext();
        context.ensureCollections();
        if (latestTurn == null || latestTurn.isBlank()) return context;
        TurnUnderstanding understanding = turnUnderstandingResultHandoff.handoff(context, latestTurn, parsedResultSupplier);
        context.appendTurnUnderstanding(understanding);
        return context;
    }
}
