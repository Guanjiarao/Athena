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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.lang.Assert;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.knowledge.config.AthenaKnowledgeSyncProperties;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeBaseRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Athena 知识库路由服务实现
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseRoutingServiceImpl implements KnowledgeBaseRoutingService {

    private final AthenaKnowledgeSyncProperties knowledgeSyncProperties;

    @Override
    public String resolveKbCodeByType(Integer type) {
        Assert.notNull(type, () -> new ClientException("笔记类型不能为空"));

        if (knowledgeSyncProperties.getCommonTypes().contains(type)) {
            return knowledgeSyncProperties.getCommonKbCode();
        }

        return knowledgeSyncProperties.getMappings().stream()
                .filter(mapping -> type >= mapping.getTypeRangeStart() && type <= mapping.getTypeRangeEnd())
                .map(AthenaKnowledgeSyncProperties.TypeRangeMapping::getKbCode)
                .findFirst()
                .orElseThrow(() -> new ClientException("未找到匹配的知识库路由，type=" + type));
    }
}
