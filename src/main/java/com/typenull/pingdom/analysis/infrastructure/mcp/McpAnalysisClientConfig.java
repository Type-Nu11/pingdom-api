package com.typenull.pingdom.analysis.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class McpAnalysisClientConfig {

    @Bean
    public McpAnalysisClient mcpAnalysisClient(
            McpAnalysisProperties properties,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new RestMcpAnalysisClient(
                RestClient.builder()
                        .baseUrl(properties.serverUrl())
                        .requestFactory(requestFactory)
                        .build(),
                objectMapper
        );
    }

}
