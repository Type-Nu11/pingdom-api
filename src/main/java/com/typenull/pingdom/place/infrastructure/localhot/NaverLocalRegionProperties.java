package com.typenull.pingdom.place.infrastructure.localhot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Naver 역지오코딩의 인증·타임아웃과 프로세스 내 캐시 설정.
 * 기본 연결/읽기 제한은 2초/3초이고 캐시는 10분·10,000개. 잘못된 기간·개수는 기본값으로 보정.
 */
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
