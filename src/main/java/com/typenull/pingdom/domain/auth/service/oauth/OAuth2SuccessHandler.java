package com.typenull.pingdom.domain.auth.service.oauth;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.OAuthAccount;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.repository.OAuthAccountRepository;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.global.config.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

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
        String email = resolveEmail(oAuth2User);

        OAuthAccount account = oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);

        if (account == null) {
            if (email == null || email.isBlank()) {
                throw new IllegalStateException("Google 사용자 이메일을 찾을 수 없습니다.");
            }

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .username(generateUniqueUsername(email))
                            .email(email)
                            .emailVerified(true)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .build()));

            account = oAuthAccountRepository.save(OAuthAccount.builder()
                    .provider(AuthProvider.GOOGLE)
                    .providerId(providerId)
                    .user(user)
                    .build());
        }

        User user = account.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        user.issueRefreshToken(refreshToken);

        // access/refresh token을 URL에 노출하지 않고, 짧은 URL로 이동 후 JSON을 반환하기 위해 쿠키로 전달한다.
        addShortLivedCookie(response, ACCESS_COOKIE, accessToken);
        addShortLivedCookie(response, REFRESH_COOKIE, refreshToken);
        response.sendRedirect("/auth/oauth2/success");
    }

    private void addShortLivedCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/auth/oauth2/success");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
        response.addCookie(cookie);
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private String resolveEmail(OAuth2User oAuth2User) {
        Object email = oAuth2User.getAttributes().get("email");
        return (email == null) ? null : String.valueOf(email);
    }

    private String generateUniqueUsername(String email) {
        String base = (email == null) ? "google_user" : email;
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }

        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int maxBaseLength = Math.max(1, 50 - 1 - suffix.length());
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        return base + "_" + suffix;
    }
}
