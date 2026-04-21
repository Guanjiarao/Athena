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

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.config.TriageSessionProperties;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * triage Redis 会话存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageSessionManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TriageSessionProperties triageSessionProperties;

    public TriageContext getContext(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        String payload = stringRedisTemplate.opsForValue().get(buildKey(sessionId));
        if (StrUtil.isBlank(payload)) {
            return null;
        }
        try {
            TriageContext context = objectMapper.readValue(payload, TriageContext.class);
            context.ensureCollections();
            return context;
        } catch (Exception ex) {
            log.warn("读取 triage Redis 会话失败, sessionId={}", sessionId, ex);
            return null;
        }
    }

    public void saveContext(TriageContext ctx) {
        if (ctx == null || StrUtil.isBlank(ctx.getSessionId())) {
            return;
        }
        ctx.ensureCollections();
        try {
            stringRedisTemplate.opsForValue().set(
                    buildKey(ctx.getSessionId()),
                    objectMapper.writeValueAsString(ctx),
                    Duration.ofMinutes(triageSessionProperties.getTtlMinutes())
            );
        } catch (Exception ex) {
            log.warn("保存 triage Redis 会话失败, sessionId={}", ctx.getSessionId(), ex);
        }
    }

    private String buildKey(String sessionId) {
        return StrUtil.blankToDefault(triageSessionProperties.getKeyPrefix(), "triage:session:") + sessionId;
    }
}
