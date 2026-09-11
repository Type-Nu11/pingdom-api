package com.typenull.pingdom.menu.infrastructure.currency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
