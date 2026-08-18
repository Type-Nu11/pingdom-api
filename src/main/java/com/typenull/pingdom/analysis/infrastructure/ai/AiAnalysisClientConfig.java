package com.typenull.pingdom.analysis.infrastructure.ai;

import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAnalysisClientConfig {

    @Bean
    @ConditionalOnMissingBean(AiAnalysisClient.class)
    public AiAnalysisClient placeholderAiAnalysisClient() {
        return new PlaceholderAiAnalysisClient();
    }
}
