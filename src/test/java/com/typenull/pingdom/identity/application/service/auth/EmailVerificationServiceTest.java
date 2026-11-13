package com.typenull.pingdom.identity.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00.123456789Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Mock private UserRepository users;
    @Mock private OutboxEventPublisher outbox;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(users, outbox, CLOCK);
    }

    @Test
    void resendPublishesCurrentCodeAndMicrosecondExpiry() {
        User user = User.builder().id(1L).email("user@example.com").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resendVerificationEmail(new EmailResendRequest(user.getEmail()));

        LocalDateTime expiry = NOW.truncatedTo(ChronoUnit.MICROS).plusMinutes(10);
        ArgumentCaptor<EmailVerificationOutboxPayload> payload = ArgumentCaptor.forClass(EmailVerificationOutboxPayload.class);
        verify(outbox).publish(eq("EMAIL_VERIFICATION:1:" + user.getEmailVerificationCode() + ":" + expiry),
                eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED), payload.capture(), eq("USER"), eq("1"));
        assertThat(user.getEmailVerificationCode()).matches("[0-9]{6}");
        assertThat(user.getEmailVerificationExpiresAt()).isEqualTo(expiry);
        assertThat(payload.getValue()).isEqualTo(
                new EmailVerificationOutboxPayload(1L, user.getEmail(), user.getEmailVerificationCode(), expiry));
    }

    @Test
    void verificationKeepsExistingInclusiveExpiryBoundary() {
        User user = User.builder().id(1L).email("user@example.com")
                .emailVerificationCode("123456").emailVerificationExpiresAt(NOW).build();
        when(users.findByEmailAndEmailVerificationCode(user.getEmail(), "123456")).thenReturn(Optional.of(user));

        service.verifyEmail(new EmailVerifyRequest(user.getEmail(), "123456"));

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationCode()).isNull();
        assertThat(user.getEmailVerificationExpiresAt()).isNull();
        verifyNoInteractions(outbox);
    }

    @Test
    void withdrawnUserIsRejectedBeforeExpiryOrVerificationMutation() {
        User user = User.builder().id(1L).email("user@example.com").status(UserStatus.WITHDRAWN)
                .emailVerificationCode("123456").emailVerificationExpiresAt(NOW.minusMinutes(1)).build();
        when(users.findByEmailAndEmailVerificationCode(user.getEmail(), "123456")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyEmail(new EmailVerifyRequest(user.getEmail(), "123456")))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN));
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerificationCode()).isEqualTo("123456");
        verifyNoInteractions(outbox);
    }

    @Test
    void verifiedUserCannotIssueAnotherCode() {
        User user = User.builder().id(1L).email("user@example.com").emailVerified(true).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resendVerificationEmail(new EmailResendRequest(user.getEmail())))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_ALREADY_VERIFIED));
        assertThat(user.isEmailVerified()).isTrue();
        verifyNoInteractions(outbox);
    }
}
