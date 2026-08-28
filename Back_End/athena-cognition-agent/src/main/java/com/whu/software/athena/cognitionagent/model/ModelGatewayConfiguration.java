package com.whu.software.athena.cognitionagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntentModelProperties.class)
public class ModelGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "athena.model", name = "provider",
            havingValue = "openai-compatible")
    public ModelGateway openAiCompatibleModelGateway(
            ObjectMapper mapper, IntentModelProperties properties) {
        return new OpenAiCompatibleModelGateway(mapper, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "athena.model", name = "provider",
            havingValue = "mock", matchIfMissing = true)
    public ModelGateway mockModelGateway(ObjectMapper mapper) {
        return new MockModelGateway(mapper);
    }
}
