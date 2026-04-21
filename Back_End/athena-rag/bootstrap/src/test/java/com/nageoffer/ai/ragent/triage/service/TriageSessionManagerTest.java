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

package com.nageoffer.ai.ragent.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.model.AuditLog;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriageSessionManagerTest {

    @Test
    void shouldSaveAndLoadContextFromRedis() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TriageSessionProperties properties = new TriageSessionProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        TriageSessionManager sessionManager = new TriageSessionManager(redisTemplate, objectMapper, properties);

        TriageContext context = TriageContext.builder().sessionId("session-1").build();
        context.appendConversation("first turn");
        context.appendState("state-1");
        context.appendAudit(AuditLog.builder().timestamp(Instant.now()).decisionBasis("test").build());

        sessionManager.saveContext(context);
        String stored = objectMapper.writeValueAsString(context);
        when(valueOperations.get("triage:session:session-1")).thenReturn(stored);

        TriageContext saved = sessionManager.getContext("session-1");
        assertNotNull(saved);
        assertEquals("session-1", saved.getSessionId());
        assertEquals(1, saved.getConversationHistory().size());
        assertEquals(1, saved.getStateLog().size());
        assertEquals(1, saved.getAuditTrail().size());
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    void shouldIgnoreInvalidSessionIdAndContext() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TriageSessionManager sessionManager = new TriageSessionManager(redisTemplate, new ObjectMapper(), new TriageSessionProperties());

        sessionManager.saveContext(null);
        sessionManager.saveContext(TriageContext.builder().build());

        assertNull(sessionManager.getContext(null));
        assertNull(sessionManager.getContext(" "));
    }
}
