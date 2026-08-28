package com.whu.software.athena.cognitionagent.model;

import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionModelProviderGuardTest {

    @Test
    void rejectsMockProviderInProduction() {
        IntentModelProperties properties = new IntentModelProperties();
        properties.setProvider("mock");

        ProductionModelProviderGuard guard = new ProductionModelProviderGuard(properties);

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void rejectsIncompleteRealProviderConfiguration() {
        IntentModelProperties properties = new IntentModelProperties();
        properties.setProvider("openai-compatible");

        ProductionModelProviderGuard guard = new ProductionModelProviderGuard(properties);

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void acceptsCompleteRealProviderConfiguration() {
        IntentModelProperties properties = new IntentModelProperties();
        properties.setProvider("openai-compatible");
        properties.setApiKey("test-only-key");
        properties.setBaseUrl("https://example.invalid/compatible-mode");
        properties.setModelName("test-model");

        ProductionModelProviderGuard guard = new ProductionModelProviderGuard(properties);

        assertDoesNotThrow(guard::afterPropertiesSet);
    }
}
