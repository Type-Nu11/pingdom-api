package com.typenull.pingdom.integration.auth;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.oauth.OAuthAccountDisconnectRequest;
import com.typenull.pingdom.identity.api.oauth.OAuth2TokenController;
import com.typenull.pingdom.identity.application.command.OAuthAccountCommandService;
import com.typenull.pingdom.identity.application.command.OAuthUserService;
import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.OAuthAccount;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.infrastructure.oauth.CustomOAuth2User;
import com.typenull.pingdom.identity.infrastructure.oauth.OAuth2LinkCookieService;
import com.typenull.pingdom.identity.infrastructure.oauth.OAuth2LinkTokenService;
import com.typenull.pingdom.identity.infrastructure.oauth.OAuth2SuccessHandler;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Google 연동·해제 권한과 성공 핸들러의 토큰 전달을 검증한다. Google 서버 호출은 실행하지 않는다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class OAuthAccountControllerTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private OAuthAccountCommandService oAuthAccountCommandService;

    @Autowired
    private OAuthUserService oAuthUserService;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OAuth2TokenController oAuth2TokenController;

    @Autowired
    private OAuth2LinkTokenService oAuth2LinkTokenService;

    /**
     * 인증된 연동 시작 요청이 연동 쿠키와 Google 인가 경로를 반환하는지 확인한다.
     */
    @Test
    void startGoogleLink() throws Exception {
        User user = createUser("oauthLinkStartUser");

        mockMvc.perform(post("/users/me/oauth-accounts/google/link")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(OAuth2LinkCookieService.COOKIE_NAME)))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.authorizationUrl").value("/oauth2/authorization/google"));
    }

    /**
     * 로컬 사용자와 이메일이 일치하면 Google provider ID 연관관계가 저장되는지 확인한다.
     */
    @Test
    void linkMatchingEmail() {
        User user = createUser("oauthLinkUser");

        oAuthAccountCommandService.linkGoogleAccount(user.getId(), "google-sub-1", user.getEmail());

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1").isPresent());
    }

    /**
     * 다른 사용자에게 이미 연결된 provider ID는 OAUTH_ACCOUNT_ALREADY_LINKED로 거절되는지 확인한다.
     */
    @Test
    void linkOwnedProviderId() {
        User owner = createUser("oauthOwnerUser");
        User target = createUser("oauthTargetUser");
        oAuthAccountRepository.saveAndFlush(OAuthAccount.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId("duplicated-google-sub")
                .user(owner)
                .build());

        AuthException exception = assertThrows(AuthException.class, () ->
                oAuthAccountCommandService.linkGoogleAccount(target.getId(), "duplicated-google-sub", target.getEmail()));

        assertEquals(AuthErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED, exception.getErrorCode());
    }

    /**
     * 로컬 사용자 이메일과 다른 Google 이메일의 연결을 OAUTH_EMAIL_MISMATCH로 거절하는지 확인한다.
     */
    @Test
    void linkMismatchedEmail() {
        User user = createUser("oauthEmailMismatchUser");

        AuthException exception = assertThrows(AuthException.class, () ->
                oAuthAccountCommandService.linkGoogleAccount(user.getId(), "google-sub-2", "other@example.com"));

        assertEquals(AuthErrorCode.OAUTH_EMAIL_MISMATCH, exception.getErrorCode());
    }

    /**
     * 기존 로컬 이메일로 새 Google 사용자를 만들 때 계정 충돌 안내 코드를 반환하는지 확인한다.
     */
    @Test
    void localEmailConflict() {
        User user = createUser("oauthEmailConflictUser");

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () ->
                oAuthUserService.provisionGoogleUser("new-google-sub", user.getEmail()));

        assertEquals(AuthErrorCode.OAUTH_EMAIL_CONFLICT.name(), exception.getError().getErrorCode());
    }

    /**
     * 비밀번호 확인 없는 마지막 Google 연결 해제를 거절하고 연관관계를 보존하는지 확인한다.
     */
    @Test
    void unlinkWithoutPassword() throws Exception {
        User user = createUser("oauthUnlinkRequiredUser");
        linkAccount(user, "unlink-required-sub");

        mockMvc.perform(delete("/users/me/oauth-accounts/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OAUTH_PASSWORD_CONFIRMATION_REQUIRED"));

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "unlink-required-sub").isPresent());
    }

    /**
     * 로컬 비밀번호가 활성화되지 않은 OAuth 전용 계정은 비밀번호를 제출해도 연결을 유지하는지 확인한다.
     */
    @Test
    void unlinkOAuthOnlyUser() throws Exception {
        User user = oAuthUserService.provisionGoogleUser("oauth-only-sub", "oauth-only@example.com");
        assertFalse(user.isLocalPasswordEnabled());
        OAuthAccountDisconnectRequest request = new OAuthAccountDisconnectRequest("password123");

        mockMvc.perform(delete("/users/me/oauth-accounts/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OAUTH_LOCAL_PASSWORD_REQUIRED"));

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "oauth-only-sub").isPresent());
    }

    /**
     * 잘못된 비밀번호로 연결 해제 시 INVALID_CREDENTIALS를 반환하고 연결 행을 보존하는지 확인한다.
     */
    @Test
    void unlinkWrongPassword() throws Exception {
        User user = createUser("oauthUnlinkInvalidUser");
        linkAccount(user, "unlink-invalid-password-sub");

        OAuthAccountDisconnectRequest request = new OAuthAccountDisconnectRequest("wrong-password");

        mockMvc.perform(delete("/users/me/oauth-accounts/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "unlink-invalid-password-sub").isPresent());
    }

    /**
     * 올바른 로컬 비밀번호 확인 후 linked=false 응답과 연결 행 삭제를 확인한다.
     */
    @Test
    void unlinkMatchingPassword() throws Exception {
        User user = createUser("oauthUnlinkUser");
        linkAccount(user, "unlink-success-sub");
        OAuthAccountDisconnectRequest request = new OAuthAccountDisconnectRequest("password123");

        mockMvc.perform(delete("/users/me/oauth-accounts/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.linked").value(false));

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "unlink-success-sub").isEmpty());
    }

    /**
     * 연동 쿠키가 있는 OAuth 성공 콜백은 연동 완료로 리다이렉트하고 기존 갱신 토큰을 유지하는지 확인한다.
     */
    @Test
    void linkPreservesRefreshToken() throws Exception {
        User user = createUser("oauthLinkSuccessUser");
        user.issueRefreshToken("existing-refresh-token");
        userRepository.saveAndFlush(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(OAuth2LinkCookieService.COOKIE_NAME, oAuth2LinkTokenService.generate(user.getId())));
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken authentication = googleAuthentication(user, "link-success-sub");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(302, response.getStatus());
        assertTrue(response.getRedirectedUrl().contains("linked=GOOGLE"));
        assertTrue(userRepository.findById(user.getId()).orElseThrow()
                .matchesRefreshToken("existing-refresh-token"));
    }

    /**
     * OAuth 로그인 후 공통 갱신 쿠키의 /auth 경로·HttpOnly 속성과 구형 쿠키 부재를 확인한다.
     */
    @Test
    void oauthRefreshCookie() throws Exception {
        User user = createUser("oauthRefreshCookieUser");
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2SuccessHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                googleAuthentication(user, "oauth-refresh-cookie-sub")
        );

        String refreshCookie = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("PINGDOM_REFRESH_TOKEN="))
                .findFirst()
                .orElseThrow();

        assertTrue(refreshCookie.contains("Path=/auth"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertFalse(refreshCookie.contains("refresh-token"));
        assertFalse(response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(header -> header.startsWith("OAUTH2_REFRESH_TOKEN=")));
    }

    /**
     * 임시 접근 쿠키 교환 시 본문에는 접근 토큰만 포함하고 응답에 쿠키 변경 헤더가 있는지 확인한다.
     */
    @Test
    void oauthAccessTokenExchange() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OAUTH2_ACCESS_TOKEN", "oauth-access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<?> tokenResponse = oAuth2TokenController.oauth2Success(request, response);

        assertEquals(200, tokenResponse.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) tokenResponse.getBody();
        assertEquals("oauth-access-token", body.get("accessToken"));
        assertFalse(body.containsKey("refreshToken"));
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("OAUTH2_ACCESS_TOKEN="));
    }

    /**
     * 연결 해제 검증을 위해 사용자와 Google provider ID의 관계를 직접 저장한다.
     */
    private void linkAccount(User user, String providerId) {
        oAuthAccountRepository.saveAndFlush(OAuthAccount.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .user(user)
                .build());
    }

    /**
     * 사용자 역할과 Google sub 속성을 가진 인증 객체를 만들어 성공 핸들러 호출에 사용한다.
     */
    private OAuth2AuthenticationToken googleAuthentication(User user, String providerId) {
        return new OAuth2AuthenticationToken(
                new CustomOAuth2User(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        AuthProvider.GOOGLE,
                        providerId,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                        Map.of("sub", providerId),
                        "sub"
                ),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                "google"
        );
    }

    /**
     * 저장된 사용자 정보로 API 호출용 접근 토큰을 직접 발급한다.
     */
    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
