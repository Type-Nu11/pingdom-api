package com.typenull.pingdom.shared.security.refresh;

import com.typenull.pingdom.shared.security.jwt.JwtProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** refresh token을 /auth 경로의 HttpOnly 쿠키로 발급·만료시키고 요청 쿠키에서 읽음. 토큰 검증은 별도. */
@Component
public class RefreshTokenCookieService {

    private static final String COOKIE_PATH = "/auth";

    private final RefreshTokenCookieProperties properties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookieService(RefreshTokenCookieProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    /** 설정된 이름의 비어 있지 않은 첫 쿠키 값을 반환. 쿠키가 없거나 값이 공백이면 empty 반환. */
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

    /** JWT refresh 수명과 같은 초 단위 max-age를 적용. 호출자가 Set-Cookie 헤더로 전달해야 함. */
    public ResponseCookie issue(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds()))
                .build();
    }

    /** 발급과 동일한 이름·경로·도메인에 빈 값과 max-age 0을 설정해 삭제용 쿠키를 생성. */
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
