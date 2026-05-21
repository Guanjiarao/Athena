

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
