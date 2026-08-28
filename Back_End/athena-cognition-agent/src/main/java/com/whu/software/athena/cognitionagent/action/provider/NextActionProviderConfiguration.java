package com.whu.software.athena.cognitionagent.action.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NextActionProviderConfiguration {

    @Bean
    public NextActionModelProvider nextActionModelProvider(
            ModelGateway gateway, ObjectMapper mapper) {
        return new GatewayNextActionModelProvider(gateway, mapper);
    }
}
