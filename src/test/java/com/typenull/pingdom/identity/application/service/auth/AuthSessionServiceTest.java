package com.typenull.pingdom.identity.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider.RefreshTokenParseResult;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider.TokenStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    @Mock private UserRepository users;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtTokenProvider jwt;
    @Mock private AuthMetrics metrics;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService(users, encoder, jwt, CLOCK, metrics);
    }

    @Test
    void refreshRotatesOnlyCurrentTokenAndRecordsSuccess() {
        User user = User.builder().id(1L).username("user").build();
        user.issueRefreshToken("current-refresh");
        when(jwt.parseRefreshToken("current-refresh")).thenReturn(new RefreshTokenParseResult(TokenStatus.VALID, 1L));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(jwt.generateAccessToken(1L, "user", "USER")).thenReturn("access");
        when(jwt.generateRefreshToken(1L)).thenReturn("rotated");

        TokenRefreshResult result = service.refreshToken("current-refresh");

        assertThat(result).isEqualTo(new TokenRefreshResult("access", "rotated"));
        assertThat(user.matchesRefreshToken("rotated")).isTrue();
        assertThat(user.matchesRefreshToken("current-refresh")).isFalse();
        verify(metrics).recordRefreshTokenSuccess();
        verify(metrics, never()).recordRefreshTokenFailure(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void staleRefreshIsRejectedWithoutRotatingNewerSession() {
        User user = User.builder().id(1L).username("user").build();
        user.issueRefreshToken("newer-refresh");
        when(jwt.parseRefreshToken("old-refresh")).thenReturn(new RefreshTokenParseResult(TokenStatus.VALID, 1L));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refreshToken("old-refresh")).isInstanceOfSatisfying(AuthException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));
        assertThat(user.matchesRefreshToken("newer-refresh")).isTrue();
        verify(metrics).recordRefreshTokenFailure("INVALID_TOKEN");
        verify(metrics, never()).recordRefreshTokenSuccess();
        verify(jwt, never()).generateRefreshToken(1L);
    }

    @Test
    void staleLogoutKeepsNewerSession() {
        User user = User.builder().id(1L).build();
        user.issueRefreshToken("newer-refresh");
        when(jwt.parseRefreshToken("old-refresh")).thenReturn(new RefreshTokenParseResult(TokenStatus.VALID, 1L));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        service.logout("old-refresh");

        assertThat(user.matchesRefreshToken("newer-refresh")).isTrue();
        verifyNoInteractions(metrics);
    }

    @Test
    void adminRoleIsRejectedBeforeBanOrPasswordChecks() {
        User user = User.builder().id(1L).username("user").banned(true).build();
        when(users.findByUsernameForUpdate("user")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.adminLogin(new LoginRequest("user", "password123")))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
        verifyNoInteractions(encoder, jwt, metrics);
    }
}
