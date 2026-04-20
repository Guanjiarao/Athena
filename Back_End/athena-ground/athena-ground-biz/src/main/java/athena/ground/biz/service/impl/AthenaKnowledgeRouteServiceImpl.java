package athena.ground.biz.service.impl;

import athena.ground.biz.config.AthenaNoteDocumentRoutingProperties;
import athena.ground.biz.service.AthenaKnowledgeRouteService;
import cn.hutool.core.lang.Assert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Athena note 知识库路由服务实现
 */
@Service
@RequiredArgsConstructor
public class AthenaKnowledgeRouteServiceImpl implements AthenaKnowledgeRouteService {

    private final AthenaNoteDocumentRoutingProperties routingProperties;

    @Override
    public KnowledgeTarget resolveTarget(Integer type) {
        Assert.notNull(type, "笔记类型不能为空");

        if (routingProperties.getCommonTypes().contains(type)) {
            AthenaNoteDocumentRoutingProperties.KnowledgeTarget target = routingProperties.getCommonTarget();
            return new KnowledgeTarget(target.getKbCode(), target.getKbId(), target.getPipelineId());
        }

        return routingProperties.getMappings().stream()
                .filter(mapping -> type >= mapping.getTypeRangeStart() && type <= mapping.getTypeRangeEnd())
                .map(AthenaNoteDocumentRoutingProperties.TypeRangeMapping::getTarget)
                .findFirst()
                .map(target -> new KnowledgeTarget(target.getKbCode(), target.getKbId(), target.getPipelineId()))
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配的知识库路由，type=" + type));
    }
}
