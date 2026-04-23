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

package com.nageoffer.ai.ragent.triage.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageContextTest {

    @Test
    void shouldEvictOldestTurnsUntilRecentWindowFitsCharBudget() {
        TriageContext context = TriageContext.builder().build();
        context.appendConversation("右下腹疼痛明显");
        context.appendConversation("还有一点恶心");
        context.appendConversation("按压时更疼");
        context.appendConversation("今天下午更明显");

        List<String> evictedTurns = context.evictOldestTurnsByCharBudget(20);

        assertFalse(evictedTurns.isEmpty());
        assertTrue(context.recentConversationChars() <= 20);
        assertEquals(List.of("右下腹疼痛明显"), evictedTurns);
        assertEquals(List.of("还有一点恶心", "按压时更疼", "今天下午更明显"), context.getConversationHistory());
    }

    @Test
    void shouldKeepAtLeastOneTurnWhenApplyingCharBudget() {
        TriageContext context = TriageContext.builder().build();
        context.appendConversation("这一轮特别长特别长特别长特别长");

        List<String> evictedTurns = context.evictOldestTurnsByCharBudget(4);

        assertTrue(evictedTurns.isEmpty());
        assertEquals(1, context.getConversationHistory().size());
        assertEquals("这一轮特别长特别长特别长特别长", context.getConversationHistory().get(0));
    }

    @Test
    void shouldBuildTranscriptWithChineseSummaryAndRecentSections() {
        TriageContext context = TriageContext.builder().build();
        context.setConversationSummary("早期摘要：腹痛伴恶心，待补充持续时间。");
        context.appendConversation("按压时更疼");
        context.appendConversation("今天下午更明显");

        String transcript = context.buildConversationTranscript(true);

        assertTrue(transcript.contains("【历史摘要】"));
        assertTrue(transcript.contains("【最近对话】"));
        assertTrue(transcript.contains("早期摘要：腹痛伴恶心，待补充持续时间。"));
        assertTrue(transcript.contains("按压时更疼"));
        assertTrue(transcript.contains("今天下午更明显"));
    }

    @Test
    void shouldExcludeSummarySectionWhenRequested() {
        TriageContext context = TriageContext.builder().build();
        context.setConversationSummary("这段摘要不应该出现");
        context.appendConversation("只看最近一轮");

        String transcript = context.buildConversationTranscript(false);

        assertFalse(transcript.contains("【历史摘要】"));
        assertTrue(transcript.contains("【最近对话】"));
        assertTrue(transcript.contains("只看最近一轮"));
    }
}
