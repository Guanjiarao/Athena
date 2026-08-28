package com.whu.software.athena.cognitionagent.model;

import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prevents a production deployment from silently serving deterministic mock output. */
@Component
@Profile("prod")
public class ProductionModelProviderGuard implements InitializingBean {

    private final IntentModelProperties properties;

    public ProductionModelProviderGuard(IntentModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!"openai-compatible".equals(properties.getProvider())) {
            throw new IllegalStateException(
                    "prod profile requires ATHENA_MODEL_PROVIDER=openai-compatible");
        }
        properties.validateForRealProvider();
    }
}
