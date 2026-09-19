package com.typenull.pingdom.place.infrastructure.localhot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place.local-hot.naver")
public record NaverLocalRegionProperties(
        Boolean enabled,
        String clientId,
        String clientSecret,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration cacheTtl,
        Integer cacheMaxEntries
) {
    private static final String DEFAULT_BASE_URL = "https://naveropenapi.apigw.ntruss.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_CACHE_MAX_ENTRIES = 10_000;

    public NaverLocalRegionProperties {
        enabled = Boolean.TRUE.equals(enabled);
        clientId = normalize(clientId);
        clientSecret = normalize(clientSecret);
        baseUrl = normalize(baseUrl);
        if (baseUrl == null) baseUrl = DEFAULT_BASE_URL;
        connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        readTimeout = positiveOrDefault(readTimeout, DEFAULT_READ_TIMEOUT);
        cacheTtl = positiveOrDefault(cacheTtl, DEFAULT_CACHE_TTL);
        if (cacheMaxEntries == null || cacheMaxEntries < 1) cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;
    }

    public boolean isConfigured() {
        return enabled && clientId != null && clientSecret != null;
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
