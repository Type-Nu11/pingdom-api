package com.typenull.pingdom.analysis.infrastructure.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.mcp")
public record McpAnalysisProperties(
        boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public McpAnalysisProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }
}
