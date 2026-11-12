package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 발송 준비 시점의 요청을 확인한다. 조회 이후 외부 전송까지의 상태 변경은 최종 인증 API가 다시 검증한다. */
@Service
@RequiredArgsConstructor
public class AuthEmailDispatchPolicy {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean canSendVerification(Long userId, String recipient, String code, LocalDateTime expiresAt) {
        // 구형 payload를 이메일로 역조회하면 탈퇴 후 재가입 계정에 요청을 연결할 수 있다.
        if (userId == null || !StringUtils.hasText(recipient) || !StringUtils.hasText(code) || expiresAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.findById(userId).orElse(null);
        return isCurrentUser(user, recipient, now)
                && !user.isEmailVerified()
                && user.matchesEmailVerificationCode(code)
                && sameExpiry(user.getEmailVerificationExpiresAt(), expiresAt)
                && !user.isEmailVerificationExpired(now);
    }

    @Transactional(readOnly = true)
    public boolean canSendPasswordReset(Long userId, String recipient, String token, LocalDateTime expiresAt) {
        if (userId == null || !StringUtils.hasText(recipient) || !StringUtils.hasText(token) || expiresAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.findById(userId).orElse(null);
        if (!isCurrentUser(user, recipient, now)) {
            return false;
        }
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(hashToken(token)).orElse(null);
        return stored != null
                && Objects.equals(stored.getUser().getId(), userId)
                && !stored.isUsed()
                && !stored.isExpired(now)
                && sameExpiry(stored.getExpiresAt(), expiresAt);
    }

    private boolean isCurrentUser(User user, String recipient, LocalDateTime now) {
        return user != null && !user.isWithdrawn() && !user.isCurrentlyBanned(now)
                && Objects.equals(user.getEmail(), recipient);
    }

    private boolean sameExpiry(LocalDateTime current, LocalDateTime requested) {
        if (current == null) {
            return false;
        }
        // TIMESTAMP(6)에 저장할 때 pgjdbc가 적용하는 정확한 반올림이다. 구형 reset의 nano payload도 동일 요청으로 비교한다.
        LocalDateTime databaseExpiry = requested.truncatedTo(ChronoUnit.MICROS);
        if (requested.getNano() % 1000 >= 500) {
            databaseExpiry = databaseExpiry.plusNanos(1000);
        }
        return current.equals(databaseExpiry);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.");
        }
    }
}
