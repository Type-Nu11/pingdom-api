package com.typenull.pingdom.auth;

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

    @Test
    void startGoogleLinkSetsLinkCookieAndReturnsAuthorizationUrl() throws Exception {
        User user = createUser("oauthLinkStartUser");

        mockMvc.perform(post("/users/me/oauth-accounts/google/link")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(OAuth2LinkCookieService.COOKIE_NAME)))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.authorizationUrl").value("/oauth2/authorization/google"));
    }

    @Test
    void linkGoogleAccountCreatesOAuthAccountWhenEmailMatches() {
        User user = createUser("oauthLinkUser");

        oAuthAccountCommandService.linkGoogleAccount(user.getId(), "google-sub-1", user.getEmail());

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1").isPresent());
    }

    @Test
    void linkGoogleAccountRejectsProviderIdAlreadyLinkedToAnotherUser() {
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

    @Test
    void linkGoogleAccountRejectsEmailMismatch() {
        User user = createUser("oauthEmailMismatchUser");

        AuthException exception = assertThrows(AuthException.class, () ->
                oAuthAccountCommandService.linkGoogleAccount(user.getId(), "google-sub-2", "other@example.com"));

        assertEquals(AuthErrorCode.OAUTH_EMAIL_MISMATCH, exception.getErrorCode());
    }

    @Test
    void oauthLoginRejectsLocalEmailConflictWithGuidanceCode() {
        User user = createUser("oauthEmailConflictUser");

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () ->
                oAuthUserService.provisionGoogleUser("new-google-sub", user.getEmail()));

        assertEquals(AuthErrorCode.OAUTH_EMAIL_CONFLICT.name(), exception.getError().getErrorCode());
    }

    @Test
    void unlinkGoogleRejectsLastOAuthAccountWithoutPasswordConfirmation() throws Exception {
        User user = createUser("oauthUnlinkRequiredUser");
        linkAccount(user, "unlink-required-sub");

        mockMvc.perform(delete("/users/me/oauth-accounts/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OAUTH_PASSWORD_CONFIRMATION_REQUIRED"));

        assertTrue(oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "unlink-required-sub").isPresent());
    }

    @Test
    void unlinkGoogleRejectsOAuthOnlyUserUntilLocalPasswordIsSet() throws Exception {
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

    @Test
    void unlinkGoogleRejectsInvalidPassword() throws Exception {
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

    @Test
    void unlinkGoogleDeletesOAuthAccountWhenPasswordMatches() throws Exception {
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

    @Test
    void oauthLinkSuccessDoesNotRotateRefreshToken() throws Exception {
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

    @Test
    void oauthLoginIssuesPersistentRefreshTokenCookie() throws Exception {
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

    @Test
    void oauthTokenExchangeReturnsOnlyAccessToken() {
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

    private void linkAccount(User user, String providerId) {
        oAuthAccountRepository.saveAndFlush(OAuthAccount.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .user(user)
                .build());
    }

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

    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
