package com.typenull.pingdom.identity.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.outbox.PasswordResetOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00.123456789Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @Mock private UserRepository users;
    @Mock private PasswordResetTokenRepository tokens;
    @Mock private PasswordEncoder encoder;
    @Mock private OutboxEventPublisher outbox;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(users, tokens, encoder, outbox, CLOCK);
    }

    @Test
    void issuanceKeepsHashMicrosecondIdentityAndInvalidationOrder() throws Exception {
        User user = User.builder().id(1L).email("user@example.com").build();
        when(users.findByEmailIgnoreCase("USER@example.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset(new PasswordResetRequest(" USER@example.com "));

        LocalDateTime issuedAt = NOW.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiry = issuedAt.plusMinutes(30);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<PasswordResetOutboxPayload> sent = ArgumentCaptor.forClass(PasswordResetOutboxPayload.class);
        InOrder ordered = inOrder(tokens, outbox);
        ordered.verify(tokens).markActiveTokensUsed(1L, issuedAt);
        ordered.verify(tokens).save(saved.capture());
        String hash = saved.getValue().getTokenHash();
        ordered.verify(outbox).publish(eq("PASSWORD_RESET:1:" + hash + ":" + expiry),
                eq(OutboxEventType.PASSWORD_RESET_REQUESTED), sent.capture(), eq("USER"), eq("1"));
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(issuedAt);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiry);
        assertThat(sent.getValue().userId()).isEqualTo(1L);
        assertThat(sent.getValue().expiresAt()).isEqualTo(expiry);
        assertThat(sent.getValue().recipientEmail()).isEqualTo(user.getEmail());
        assertThat(sent.getValue().resetToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(hash).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(sent.getValue().resetToken().getBytes(StandardCharsets.UTF_8))));
        assertThat(hash).isNotEqualTo(sent.getValue().resetToken());
    }

    @Test
    void confirmationLocksTokenThenUserAndRevokesAllSessionsAndTokens() {
        User user = User.builder().id(1L).email("user@example.com").password("old-hash").build();
        user.issueRefreshToken("old-refresh");
        PasswordResetToken token = PasswordResetToken.create(user, HASH, NOW.plusMinutes(1), NOW.minusMinutes(1));
        when(tokens.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(encoder.encode("newPassword123")).thenReturn("new-hash");

        service.confirmPasswordReset(new PasswordResetConfirmRequest(
                " USER@example.com ", "abc", "newPassword123", "newPassword123"));

        InOrder ordered = inOrder(tokens, users);
        ordered.verify(tokens).findByTokenHashForUpdate(HASH);
        ordered.verify(users).findByIdForUpdate(1L);
        ordered.verify(tokens).markActiveTokensUsed(1L, NOW);
        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getRefreshToken()).isNull();
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verifyNoInteractions(outbox);
    }

    @Test
    void expiredTokenAtExactBoundaryDoesNotLockOrChangeUser() {
        User user = User.builder().id(1L).email("user@example.com").build();
        PasswordResetToken token = PasswordResetToken.create(user, HASH, NOW, NOW.minusMinutes(30));
        when(tokens.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmPasswordReset(new PasswordResetConfirmRequest(
                user.getEmail(), "abc", "newPassword123", "newPassword123")))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.EXPIRED_PASSWORD_RESET_TOKEN));
        assertThat(token.isUsed()).isFalse();
        verifyNoInteractions(users, encoder, outbox);
    }

    @Test
    void missingEmailDoesNotPublishAndDoesNotRevealAccountExistence() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(new PasswordResetRequest(" missing@example.com "));

        verifyNoInteractions(tokens, encoder, outbox);
    }
}
