package com.typenull.pingdom.consultation.infrastructure.gemini;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        Boolean enabled,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    public GeminiProperties {
        enabled = Boolean.TRUE.equals(enabled);
        apiKey = normalize(apiKey);
        model = normalize(model);
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        if (connectTimeout == null || !connectTimeout.isPositive()) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null || !readTimeout.isPositive()) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
