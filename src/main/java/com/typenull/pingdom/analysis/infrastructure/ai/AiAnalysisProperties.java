package com.typenull.pingdom.analysis.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 로컬 AI 공급자를 환경변수로 교체할 수 있도록 하는 설정이다. */
@ConfigurationProperties(prefix = "analysis.ai")
public record AiAnalysisProperties(
        String provider,
        String baseUrl,
        String model,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiAnalysisProperties {
        provider = defaultValue(provider, "placeholder");
        baseUrl = defaultValue(baseUrl, defaultBaseUrl(provider));
        model = defaultValue(model, defaultModel(provider));
        apiKey = apiKey == null ? "" : apiKey;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(2) : readTimeout;
    }

    public AiAnalysisProperties(
            String provider,
            String baseUrl,
            String model,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this(provider, baseUrl, model, "", connectTimeout, readTimeout);
    }

    private static String defaultBaseUrl(String provider) {
        return "gemini".equalsIgnoreCase(provider)
                ? "https://generativelanguage.googleapis.com/v1beta"
                : "http://localhost:11434";
    }

    private static String defaultModel(String provider) {
        return "gemini".equalsIgnoreCase(provider) ? "gemini-2.5-flash" : "qwen2.5:7b";
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
