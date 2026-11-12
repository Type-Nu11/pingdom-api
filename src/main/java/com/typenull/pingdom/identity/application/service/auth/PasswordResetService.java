package com.typenull.pingdom.identity.application.service.auth;

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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AuthService facade의 쓰기 트랜잭션에 참여해 비밀번호 재설정 발급·확정을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private static final long PASSWORD_RESET_EXPIRATION_MINUTES = 30L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(user -> !user.isWithdrawn())
                .filter(user -> !user.isCurrentlyBanned(now()))
                .ifPresent(this::issuePasswordReset);
    }

    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        request.validatePassword();

        LocalDateTime now = now();
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashForUpdate(passwordResetTokenHash(request.token()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        if (resetToken.isUsed()) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        if (resetToken.isExpired(now)) {
            throw new AuthException(AuthErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        }

        User user = userRepository.findByIdForUpdate(resetToken.getUser().getId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.email().trim())) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (user.isCurrentlyBanned(now)) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();
        resetToken.markUsed(now);
        passwordResetTokenRepository.markActiveTokensUsed(user.getId(), now);
    }

    private void issuePasswordReset(User user) {
        LocalDateTime issuedAt = now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LocalDateTime expiresAt = issuedAt.plusMinutes(PASSWORD_RESET_EXPIRATION_MINUTES);
        String resetToken = generatePasswordResetToken();
        String tokenHash = passwordResetTokenHash(resetToken);

        passwordResetTokenRepository.markActiveTokensUsed(user.getId(), issuedAt);
        passwordResetTokenRepository.save(PasswordResetToken.create(user, tokenHash, expiresAt, issuedAt));
        storePasswordResetOutboxEvent(user, resetToken, tokenHash, expiresAt);
    }

    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String passwordResetTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }

    private void storePasswordResetOutboxEvent(
            User user,
            String resetToken,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        outboxEventPublisher.publish(
                "PASSWORD_RESET:%s:%s:%s".formatted(user.getId(), tokenHash, expiresAt),
                OutboxEventType.PASSWORD_RESET_REQUESTED,
                new PasswordResetOutboxPayload(user.getId(), user.getEmail(), resetToken, expiresAt),
                "USER",
                String.valueOf(user.getId())
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
