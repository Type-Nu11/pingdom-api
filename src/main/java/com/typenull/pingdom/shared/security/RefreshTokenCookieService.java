package com.typenull.pingdom.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefreshTokenCookieService {

    private static final String COOKIE_PATH = "/auth";

    private final RefreshTokenCookieProperties properties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookieService(RefreshTokenCookieProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (properties.name().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public ResponseCookie issue(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()))
                .build();
    }

    public ResponseCookie expire() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.name(), value)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite());

        if (StringUtils.hasText(properties.domain())) {
            builder.domain(properties.domain());
        }
        return builder;
    }
}
