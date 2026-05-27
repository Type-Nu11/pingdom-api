package com.typenull.pingdom.domain.auth.service.oauth;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.OAuthAccount;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.repository.OAuthAccountRepository;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.global.config.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountRepository oAuthAccountRepository;
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
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2User oAuth2User)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 principal이 아닙니다.");
            return;
        }

        String providerId = resolveProviderId(oAuth2User);
        OAuthAccount account = oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElseThrow(() -> new IllegalStateException("OAuthAccount를 찾을 수 없습니다."));

        User user = account.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        user.issueRefreshToken(refreshToken);

        boolean secureCookie = isSecureCookieRequest(request);

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

    private boolean isSecureCookieRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
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

    private String resolveProviderId(OAuth2User oAuth2User) {
        Object sub = oAuth2User.getAttributes().get("sub");
        if (sub != null) {
            return String.valueOf(sub);
        }

        Object id = oAuth2User.getAttributes().get("id");
        if (id != null) {
            return String.valueOf(id);
        }

        throw new IllegalStateException("Google 사용자 식별자를 찾을 수 없습니다.");
    }
}
