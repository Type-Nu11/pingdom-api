package com.typenull.pingdom.analysis.infrastructure.mcp;

import com.typenull.pingdom.analysis.application.ai.McpAnalysisDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class McpAnalysisDataProviderConfig {

    @Bean
    public McpAnalysisDataProvider mcpAnalysisDataProvider(McpAnalysisProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new RestMcpAnalysisDataProvider(
                RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(requestFactory)
                        .build(),
                properties
        );
    }
}
