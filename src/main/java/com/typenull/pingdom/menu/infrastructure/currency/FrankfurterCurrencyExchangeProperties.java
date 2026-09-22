package com.typenull.pingdom.menu.infrastructure.currency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메뉴 환율 조회의 활성 여부, 외부 주소와 시간 제한을 바인딩합니다.
 * 활성 여부 미설정은 true이고 시간 값이 null·0·음수이면 연결 2초, 읽기 3초, 캐시 10분 기본값을 사용합니다.
 */
@ConfigurationProperties(prefix = "menu.currency-exchange")
public record FrankfurterCurrencyExchangeProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration cacheTtl
) {
    private static final String DEFAULT_BASE_URL = "https://api.frankfurter.dev/v2";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    public FrankfurterCurrencyExchangeProperties {
        enabled = !Boolean.FALSE.equals(enabled);
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        readTimeout = positiveOrDefault(readTimeout, DEFAULT_READ_TIMEOUT);
        cacheTtl = positiveOrDefault(cacheTtl, DEFAULT_CACHE_TTL);
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
