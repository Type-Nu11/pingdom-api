package com.typenull.pingdom.identity.infrastructure.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class OAuth2LinkCookieService {

    public static final String COOKIE_NAME = "OAUTH2_LINK_TOKEN";
    private static final int COOKIE_EXPIRE_SECONDS = 300;
    private static final String COOKIE_PATH = "/";

    public ResponseCookie createLinkCookie(HttpServletRequest request, String token) {
        return baseCookie(request, token)
                .maxAge(COOKIE_EXPIRE_SECONDS)
                .build();
    }

    public ResponseCookie clearLinkCookie(HttpServletRequest request) {
        return baseCookie(request, "")
                .maxAge(0)
                .build();
    }

    public void clearLinkCookieIfPresent(HttpServletRequest request, HttpServletResponse response) {
        if (readToken(request).isPresent()) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearLinkCookie(request).toString());
        }
    }

    public Optional<String> readToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        return readToken(attributes.getRequest());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(HttpServletRequest request, String value) {
        boolean secureCookie = request.isSecure();
        return ResponseCookie.from(COOKIE_NAME, value)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(secureCookie ? "None" : "Lax");
    }
}
