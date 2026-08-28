package com.whu.software.athena.cognitionagent.target.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.model.ModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphTargetProviderConfiguration {

    @Bean
    public GraphTargetModelProvider graphTargetModelProvider(
            ModelGateway gateway, ObjectMapper mapper) {
        return new GatewayGraphTargetModelProvider(gateway, mapper);
    }
}
