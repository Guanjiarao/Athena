package athena.ground.biz.service.impl;

import athena.ground.biz.config.AthenaRagAskRoutingProperties;
import athena.ground.biz.service.RagAskRoutingService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 问答知识库路由服务实现
 */
@Service
public class RagAskRoutingServiceImpl implements RagAskRoutingService {

    private final AthenaRagAskRoutingProperties askRoutingProperties;

    public RagAskRoutingServiceImpl(AthenaRagAskRoutingProperties askRoutingProperties) {
        this.askRoutingProperties = askRoutingProperties;
    }

    @Override
    public List<String> resolveKbCodesByAge(Integer age) {
        Set<String> kbCodes = new LinkedHashSet<>();

        if (age != null && !CollectionUtils.isEmpty(askRoutingProperties.getAgeRanges())) {
            askRoutingProperties.getAgeRanges().stream()
                    .filter(range -> age >= range.getMinAge() && age <= range.getMaxAge())
                    .map(AthenaRagAskRoutingProperties.AgeRangeMapping::getKbCode)
                    .findFirst()
                    .ifPresent(kbCodes::add);
        }

        kbCodes.add(askRoutingProperties.getFallbackKbCode());
        return new ArrayList<>(kbCodes);
    }
}
