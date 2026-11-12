package com.typenull.pingdom.identity.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthEmailDispatchPolicyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final LocalDateTime EXPIRY = NOW.plusMinutes(10);
    private static final String EMAIL = "user@example.com";
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Mock private UserRepository users;
    @Mock private PasswordResetTokenRepository tokens;
    private AuthEmailDispatchPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AuthEmailDispatchPolicy(users, tokens, CLOCK);
    }

    @Test
    void sendsOnlyCurrentVerificationRequest() {
        User user = user();
        when(users.findById(1L)).thenReturn(Optional.of(user));

        assertThat(policy.canSendVerification(1L, EMAIL, "123456", EXPIRY)).isTrue();
        assertThat(policy.canSendVerification(1L, EMAIL, "654321", EXPIRY)).isFalse();
        assertThat(policy.canSendVerification(1L, "other@example.com", "123456", EXPIRY)).isFalse();
        // 재발급에서 같은 6자리 코드가 선택되어도 발급 만료가 다르면 이전 요청이다.
        assertThat(policy.canSendVerification(1L, EMAIL, "123456", EXPIRY.minusMinutes(1))).isFalse();
        user.verifyEmail();
        assertThat(policy.canSendVerification(1L, EMAIL, "123456", EXPIRY)).isFalse();
    }

    @Test
    void rejectsUnverifiableLegacyPayloadWithoutEmailFallback() {
        assertThat(policy.canSendVerification(null, EMAIL, "123456", EXPIRY)).isFalse();
        assertThat(policy.canSendVerification(1L, EMAIL, "123456", null)).isFalse();
        assertThat(policy.canSendPasswordReset(null, EMAIL, "abc", EXPIRY)).isFalse();
        verifyNoInteractions(users, tokens);
    }

    @Test
    void preservesDifferentFinalApiExpiryBoundaries() {
        User user = user();
        user.issueEmailVerification("123456", NOW);
        PasswordResetToken token = PasswordResetToken.create(user, HASH, NOW, NOW.minusMinutes(10));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(HASH)).thenReturn(Optional.of(token));

        assertThat(policy.canSendVerification(1L, EMAIL, "123456", NOW)).isTrue();
        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", NOW)).isFalse();
        user.issueEmailVerification("123456", NOW.minusNanos(1000));
        assertThat(policy.canSendVerification(1L, EMAIL, "123456", NOW.minusNanos(1000))).isFalse();
    }

    @Test
    void rejectsMissingWithdrawnAndBannedUsers() {
        List<User> blocked = List.of(
                User.builder().id(1L).email(EMAIL).status(UserStatus.WITHDRAWN).build(),
                User.builder().id(1L).email(EMAIL).banned(true).build()
        );
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThat(policy.canSendVerification(1L, EMAIL, "123456", EXPIRY)).isFalse();
        for (User user : blocked) {
            when(users.findById(1L)).thenReturn(Optional.of(user));
            assertThat(policy.canSendVerification(1L, EMAIL, "123456", EXPIRY)).isFalse();
            assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY)).isFalse();
        }
        verifyNoInteractions(tokens);
    }

    @Test
    void checksStoredResetHashOwnerExpiryAndUsedState() {
        User user = user();
        PasswordResetToken token = PasswordResetToken.create(user, HASH, EXPIRY, NOW);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(HASH)).thenReturn(Optional.of(token));

        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY)).isTrue();
        verify(tokens).findByTokenHash(HASH);
        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY.plusMinutes(1))).isFalse();
        token.markUsed(NOW);
        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY)).isFalse();
    }

    @Test
    void rejectsResetTokenBelongingToAnotherUserOrMissingToken() {
        User user = user();
        User other = User.builder().id(2L).email(EMAIL).build();
        PasswordResetToken token = PasswordResetToken.create(other, HASH, EXPIRY, NOW);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(HASH)).thenReturn(Optional.of(token), Optional.empty());

        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY)).isFalse();
        assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", EXPIRY)).isFalse();
    }

    @Test
    void matchesLegacyResetOnlyAfterExactDatabaseRounding() {
        User user = user();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        for (int nanos : new int[] {123456499, 123456500, 999999500}) {
            LocalDateTime requested = EXPIRY.withNano(nanos);
            LocalDateTime stored = nanos % 1000 >= 500
                    ? requested.plusNanos(1000 - nanos % 1000) : requested.minusNanos(nanos % 1000);
            PasswordResetToken token = PasswordResetToken.create(user, HASH, stored, NOW);
            when(tokens.findByTokenHash(HASH)).thenReturn(Optional.of(token));
            assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", requested)).isTrue();
            assertThat(policy.canSendPasswordReset(1L, EMAIL, "abc", requested.plusNanos(1000))).isFalse();
        }
    }

    private User user() {
        return User.builder().id(1L).email(EMAIL)
                .emailVerificationCode("123456").emailVerificationExpiresAt(EXPIRY).build();
    }
}
