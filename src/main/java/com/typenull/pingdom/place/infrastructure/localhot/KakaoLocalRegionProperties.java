package com.typenull.pingdom.place.infrastructure.localhot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao 좌표 지역 해석의 활성화·연결 설정과 로컬 캐시 제한입니다.
 * 기본 타임아웃은 연결 2초·읽기 3초, 캐시는 10분·10,000개이며 enabled와 API 키가 모두 있어야 사용 가능합니다.
 */
@ConfigurationProperties(prefix = "place.local-hot.kakao")
public record KakaoLocalRegionProperties(
        Boolean enabled,
        String apiKey,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration cacheTtl,
        Integer cacheMaxEntries
) {

    private static final String DEFAULT_BASE_URL = "https://dapi.kakao.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_CACHE_MAX_ENTRIES = 10_000;

    public KakaoLocalRegionProperties {
        enabled = Boolean.TRUE.equals(enabled);
        apiKey = normalize(apiKey);
        baseUrl = normalize(baseUrl);
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        readTimeout = positiveOrDefault(readTimeout, DEFAULT_READ_TIMEOUT);
        cacheTtl = positiveOrDefault(cacheTtl, DEFAULT_CACHE_TTL);
        if (cacheMaxEntries == null || cacheMaxEntries < 1) {
            cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;
        }
    }

    public boolean isConfigured() {
        return enabled && apiKey != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
