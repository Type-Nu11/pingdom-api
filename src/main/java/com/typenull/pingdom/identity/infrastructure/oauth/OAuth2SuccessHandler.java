package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${oauth2.redirect-uri:http://localhost:5173/oauth2/redirect}")
    private String redirectUri;

    private static final String ACCESS_COOKIE = "OAUTH2_ACCESS_TOKEN";
    private static final String REFRESH_COOKIE = "OAUTH2_REFRESH_TOKEN";
    private static final int COOKIE_EXPIRE_SECONDS = 60;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 인증 객체가 아닙니다.");
            return;
        }

        Object principal = oauthToken.getPrincipal();
        Long userId;
        String username;
        String roleName;

        if (principal instanceof CustomOAuth2User customUser) {
            userId = customUser.getUserId();
            username = customUser.getUsername();
            roleName = customUser.getRole().name();
        } else if (principal instanceof CustomOidcUser customOidcUser) {
            userId = customOidcUser.getUserId();
            username = customOidcUser.getUsername();
            roleName = customOidcUser.getRole().name();
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 principal 타입이 올바르지 않습니다.");
            return;
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userId, username, roleName);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User를 찾을 수 없습니다."));
        user.issueRefreshToken(refreshToken);

        boolean secureCookie = request.isSecure();

        // access/refresh token을 URL에 노출하지 않고, /auth/oauth2/success 호출로 토큰을 회수할 수 있도록 쿠키로 전달한다.
        addShortLivedCookie(response, ACCESS_COOKIE, accessToken, secureCookie);
        addShortLivedCookie(response, REFRESH_COOKIE, refreshToken, secureCookie);
        response.sendRedirect(normalizeRedirectUri(redirectUri));
    }

    private void addShortLivedCookie(HttpServletResponse response, String name, String value, boolean secureCookie) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/auth/oauth2/success")
                .httpOnly(true)
                // Cross-site 쿠키 전달을 위해서는 SameSite=None + Secure=true 조합이 필요함.
                // 단, HTTP(localhost 등)에서는 브라우저가 Secure 쿠키를 거부하므로 요청 스킴에 맞춰 동적으로 설정한다.
                .secure(secureCookie)
                .sameSite(secureCookie ? "None" : "Lax")
                .maxAge(COOKIE_EXPIRE_SECONDS)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String normalizeRedirectUri(String value) {
        if (value == null) {
            return "http://localhost:5173/oauth2/redirect";
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "http://localhost:5173/oauth2/redirect";
        }

        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }

    // providerId는 CustomOAuth2UserService에서 nameAttributeKey(sub)로 지정했으므로 getName()으로 가져온다.
}
