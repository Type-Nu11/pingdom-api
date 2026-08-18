package com.typenull.pingdom.analysis.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 로컬 AI 공급자를 환경변수로 교체할 수 있도록 하는 설정이다. */
@ConfigurationProperties(prefix = "analysis.ai")
public record AiAnalysisProperties(
        String provider,
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiAnalysisProperties {
        provider = defaultValue(provider, "placeholder");
        baseUrl = defaultValue(baseUrl, "http://localhost:11434");
        model = defaultValue(model, "qwen2.5:7b");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(2) : readTimeout;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
