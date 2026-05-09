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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TurnUnderstandingWorker {
    private final TurnUnderstandingExecutionEngine turnUnderstandingExecutionEngine;

    @Autowired
    public TurnUnderstandingWorker(LLMService llmService, ObjectMapper objectMapper) {
        this(llmService, objectMapper, new ComplaintFallbackResolver(), new TurnUnderstandingExecutionChainFactory());
    }

    TurnUnderstandingWorker(LLMService llmService,
                            ObjectMapper objectMapper,
                            ComplaintFallbackResolver complaintFallbackResolver) {
        this(llmService, objectMapper, complaintFallbackResolver, new TurnUnderstandingExecutionChainFactory());
    }

    TurnUnderstandingWorker(LLMService llmService,
                            ObjectMapper objectMapper,
                            ComplaintFallbackResolver complaintFallbackResolver,
                            TurnUnderstandingExecutionChainFactory executionChainFactory) {
        this.turnUnderstandingExecutionEngine = executionChainFactory.create(
                llmService,
                objectMapper,
                complaintFallbackResolver,
                buildSlotAnswerInferenceHelper(complaintFallbackResolver));
    }

    public TriageContext execute(TriageContext context) {
        return turnUnderstandingExecutionEngine.execute(context);
    }

    private static SlotAnswerInferenceHelper buildSlotAnswerInferenceHelper(ComplaintFallbackResolver complaintFallbackResolver) { return new SlotAnswerInferenceHelper(complaintFallbackResolver); }
}
