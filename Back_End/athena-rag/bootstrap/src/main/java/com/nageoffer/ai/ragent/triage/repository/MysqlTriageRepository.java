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

package com.nageoffer.ai.ragent.triage.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.triage.dao.entity.TriageSessionRecordDO;
import com.nageoffer.ai.ragent.triage.dao.mapper.TriageSessionRecordMapper;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.engine.TriageState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * triage 终态结果 MySQL 持久化实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MysqlTriageRepository implements TriageRepository {

    private final TriageSessionRecordMapper triageSessionRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void save(TriageContext context) {
        if (context == null || StrUtil.isBlank(context.getSessionId())) {
            return;
        }
        context.ensureCollections();
        TriageSessionRecordDO record = toRecord(context);

        LambdaQueryWrapper<TriageSessionRecordDO> queryWrapper = Wrappers.lambdaQuery(TriageSessionRecordDO.class)
                .eq(TriageSessionRecordDO::getSessionId, context.getSessionId());
        TriageSessionRecordDO existing = triageSessionRecordMapper.selectOne(queryWrapper);
        if (existing == null) {
            triageSessionRecordMapper.insert(record);
            return;
        }
        record.setId(existing.getId());
        triageSessionRecordMapper.updateById(record);
    }

    @Override
    public TriageContext findBySessionId(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        TriageSessionRecordDO record = triageSessionRecordMapper.selectOne(
                Wrappers.lambdaQuery(TriageSessionRecordDO.class)
                        .eq(TriageSessionRecordDO::getSessionId, sessionId)
        );
        if (record == null) {
            return null;
        }
        return TriageContext.builder()
                .sessionId(record.getSessionId())
                .userInput(record.getUserInputSnapshot())
                .currentState(parseEnum(record.getCurrentState(), TriageState.class))
                .nextAction(parseEnum(record.getNextAction(), TriageAction.class))
                .riskAssessment(parseJson(record.getRiskAssessmentJson(), RiskLevel.class))
                .finalReply(record.getFinalReply())
                .conversationHistory(parseJsonList(record.getConversationHistoryJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}))
                .extractedSymptoms(parseJsonList(record.getExtractedSymptomsJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.nageoffer.ai.ragent.triage.model.Symptom>>() {}))
                .missingFields(parseJsonList(record.getMissingFieldsJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}))
                .stateLog(parseJsonList(record.getStateLogJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}))
                .auditTrail(parseJsonList(record.getAuditTrailJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.nageoffer.ai.ragent.triage.model.AuditLog>>() {}))
                .build();
    }

    private TriageSessionRecordDO toRecord(TriageContext context) {
        return TriageSessionRecordDO.builder()
                .sessionId(context.getSessionId())
                .userId(UserContext.getUserId())
                .currentState(context.getCurrentState() == null ? null : context.getCurrentState().name())
                .nextAction(context.getNextAction() == null ? null : context.getNextAction().name())
                .riskLevel(context.getRiskAssessment() == null ? null : context.getRiskAssessment().getLevel())
                .riskScore(context.getRiskAssessment() == null ? null : context.getRiskAssessment().getScore())
                .finalReply(context.getFinalReply())
                .userInputSnapshot(context.getUserInput())
                .conversationHistoryJson(writeJson(context.getConversationHistory()))
                .extractedSymptomsJson(writeJson(context.getExtractedSymptoms()))
                .missingFieldsJson(writeJson(context.getMissingFields()))
                .riskAssessmentJson(writeJson(context.getRiskAssessment()))
                .stateLogJson(writeJson(context.getStateLog()))
                .auditTrailJson(writeJson(context.getAuditTrail()))
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("triage 结果序列化失败", ex);
            return null;
        }
    }

    private <T> T parseJson(String json, Class<T> clazz) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            log.warn("triage 结果反序列化失败", ex);
            return null;
        }
    }

    private <T> T parseJsonList(String json, com.fasterxml.jackson.core.type.TypeReference<T> typeReference) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            log.warn("triage 列表反序列化失败", ex);
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> clazz) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(clazz, raw);
        } catch (Exception ex) {
            log.warn("triage 枚举反序列化失败: {}", raw, ex);
            return null;
        }
    }
}
