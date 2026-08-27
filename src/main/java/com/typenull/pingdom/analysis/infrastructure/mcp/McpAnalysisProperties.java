package com.typenull.pingdom.analysis.infrastructure.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.mcp")
public record McpAnalysisProperties(
        String serverUrl,
        String authToken
) {

    public McpAnalysisProperties {
        serverUrl = serverUrl == null || serverUrl.isBlank()
                ? ""
                : serverUrl;
        authToken = authToken == null ? "" : authToken;
    }
}
