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

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.model.TurnUnderstanding;
import com.nageoffer.ai.ragent.triage.prompt.TurnUnderstandingPromptTemplates;

public class TurnUnderstandingExecutionEngine extends AbstractStructuredTriageWorker {
    private final TurnUnderstandingExecutionShell turnUnderstandingExecutionShell;

    public TurnUnderstandingExecutionEngine(LLMService llmService,
                                            ObjectMapper objectMapper,
                                            TurnUnderstandingExecutionShell turnUnderstandingExecutionShell) {
        super(llmService, objectMapper);
        this.turnUnderstandingExecutionShell = turnUnderstandingExecutionShell;
    }

    public TriageContext execute(TriageContext context) {
        String latestTurn = context == null ? null : StrUtil.blankToDefault(context.getLatestUserTurn(), "").trim();
        return turnUnderstandingExecutionShell.execute(
                context,
                latestTurn,
                () -> {
                    String raw = invokeLlm(buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D);
                    return readObjectSafely(raw, TurnUnderstanding.class, null, "回合语义理解");
                });
    }

    private String buildSystemPrompt() { return TurnUnderstandingPromptTemplates.systemPrompt(); }
    private String buildUserPrompt(TriageContext context) { return TurnUnderstandingPromptTemplates.userPrompt(StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"), StrUtil.blankToDefault(context.getLatestUserTurn(), ""), StrUtil.blankToDefault(context.buildConversationTranscript(true), "无"), toJsonSafely(context.getLastAskedSlots()), toJsonSafely(context.getPendingSlots()), toJsonSafely(context.getSlotState())); }
}
