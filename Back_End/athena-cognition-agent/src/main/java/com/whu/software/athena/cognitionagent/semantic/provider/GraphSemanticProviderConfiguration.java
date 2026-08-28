package com.whu.software.athena.cognitionagent.semantic.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphSemanticProviderConfiguration {

    @Bean
    public GraphSemanticModelProvider graphSemanticModelProvider(
            ModelGateway gateway, ObjectMapper mapper) {
        return new GatewayGraphSemanticModelProvider(gateway, mapper);
    }
}
