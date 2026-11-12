package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** AuthService facade의 쓰기 트랜잭션에 참여해 이메일 인증 코드와 발송 요청을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10L;

    private final UserRepository userRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    public void resendVerificationEmail(EmailResendRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isCurrentlyBanned(now())) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (user.isEmailVerified()) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        issueEmailVerification(user);
        storeEmailVerificationOutboxEvent(user);
    }

    public void verifyEmail(EmailVerifyRequest request) {
        User user = userRepository.findByEmailAndEmailVerificationCode(request.email(), request.code())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE));

        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (user.isEmailVerificationExpired(now())) {
            throw new AuthException(AuthErrorCode.EXPIRED_EMAIL_VERIFICATION_CODE);
        }

        // 이메일 인증 상태 반영 호출
        user.verifyEmail();
    }

    private String generateVerificationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }

    void issueEmailVerification(User user) {
        user.issueEmailVerification(
                generateVerificationCode(),
                now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES)
        );
    }

    void storeEmailVerificationOutboxEvent(User user) {
        if (StringUtils.hasText(user.getEmail()) && StringUtils.hasText(user.getEmailVerificationCode())) {
            outboxEventPublisher.publish(
                    "EMAIL_VERIFICATION:%s:%s:%s".formatted(
                            user.getId(),
                            user.getEmailVerificationCode(),
                            user.getEmailVerificationExpiresAt()
                    ),
                    OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                    new EmailVerificationOutboxPayload(user.getId(), user.getEmail(), user.getEmailVerificationCode(), user.getEmailVerificationExpiresAt()),
                    "USER",
                    String.valueOf(user.getId())
            );
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
