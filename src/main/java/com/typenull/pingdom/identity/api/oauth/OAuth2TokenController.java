package com.typenull.pingdom.identity.api.oauth;

import jakarta.servlet.http.Cookie;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * OAuth 성공 핸들러가 남긴 짧은 수명 쿠키를 읽어 access token을 응답 본문으로 전달.
 * 쿠키는 응답에서 즉시 만료시키며 쿠키 값의 JWT 검증은 이 전달 API의 처리 범위에서 제외.
 */
@RestController
public class OAuth2TokenController {

    private static final String ACCESS_COOKIE = "OAUTH2_ACCESS_TOKEN";

    @GetMapping("/auth/oauth2/success")
    @Operation(summary = "OAuth2 로그인 성공 토큰 교환")
    public ResponseEntity<?> oauth2Success(HttpServletRequest request, HttpServletResponse response) {
        boolean secureCookie = request.isSecure();
        String accessToken = readCookie(request, ACCESS_COOKIE);

        clearCookie(response, ACCESS_COOKIE, secureCookie);

        if (!StringUtils.hasText(accessToken)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "OAUTH2_TOKEN_MISSING");
            body.put("message", "OAuth2 로그인 토큰을 찾을 수 없습니다. 다시 로그인 해주세요.");
            return ResponseEntity.status(401).body(body);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "OAuth2 로그인에 성공했습니다.");
        body.put("tokenType", "Bearer");
        body.put("accessToken", accessToken);
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
