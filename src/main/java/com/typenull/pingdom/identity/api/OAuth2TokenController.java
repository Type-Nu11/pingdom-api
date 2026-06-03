package com.typenull.pingdom.identity.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2TokenController {

    private static final String ACCESS_COOKIE = "OAUTH2_ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "OAUTH2_REFRESH_TOKEN";

    @GetMapping("/auth/oauth2/success")
    public ResponseEntity<?> oauth2Success(HttpServletRequest request, HttpServletResponse response) {
        boolean secureCookie = request.isSecure();
        String accessToken = readCookie(request, ACCESS_COOKIE);
        String refreshToken = readCookie(request, REFRESH_COOKIE);

        clearCookie(response, ACCESS_COOKIE, secureCookie);
        clearCookie(response, REFRESH_COOKIE, secureCookie);

        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(refreshToken)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "OAUTH2_TOKEN_MISSING");
            body.put("message", "OAuth2 로그인 토큰을 찾을 수 없습니다. 다시 로그인 해주세요.");
            return ResponseEntity.status(401).body(body);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "OAuth2 로그인에 성공했습니다.");
        body.put("tokenType", "Bearer");
        body.put("accessToken", accessToken);
        body.put("refreshToken", refreshToken);
        return ResponseEntity.ok(body);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response, String name, boolean secureCookie) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .path("/auth/oauth2/success")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(secureCookie ? "None" : "Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
