package com.typenull.pingdom.analysis.infrastructure.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.mcp")
public record McpAnalysisProperties(
        String serverUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public McpAnalysisProperties {
        serverUrl = serverUrl == null || serverUrl.isBlank()
                ? "http://localhost:8081/mcp"
                : serverUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }
}
