package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiAnalysisClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "analysis.ai", name = "provider", havingValue = "ollama")
    public AiAnalysisClient ollamaAiAnalysisClient(AiAnalysisProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new OllamaAiAnalysisClient(restClient, properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analysis.ai", name = "provider", havingValue = "gemini")
    public AiAnalysisClient geminiAiAnalysisClient(AiAnalysisProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(withTrailingSlash(properties.baseUrl()))
                .requestFactory(requestFactory)
                .build();
        return new GeminiAiAnalysisClient(restClient, properties, objectMapper);
    }

    private String withTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Bean
    @ConditionalOnMissingBean(AiAnalysisClient.class)
    @ConditionalOnProperty(
            prefix = "analysis.ai",
            name = "provider",
            havingValue = "placeholder",
            matchIfMissing = true
    )
    public AiAnalysisClient placeholderAiAnalysisClient() {
        return new PlaceholderAiAnalysisClient();
    }
}
