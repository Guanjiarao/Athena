package com.whu.software.athena.cognitionagent.intent.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntentModelProviderConfiguration {

    @Bean
    public IntentModelProvider intentModelProvider(
            ModelGateway gateway, ObjectMapper objectMapper) {
        return new GatewayIntentModelProvider(gateway, objectMapper);
    }
}
