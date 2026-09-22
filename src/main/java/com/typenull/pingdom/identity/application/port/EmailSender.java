package com.typenull.pingdom.identity.application.port;

import java.time.LocalDateTime;

/**
 * 인증·비밀번호 복구 메일을 보내는 외부 발송 포트.
 * 재설정 메일은 기본 구현에서 미지원 예외를 던지므로 지원 어댑터의 재정의 필요.
 */
// 인증 메일 발송 추상화 인터페이스
public interface EmailSender {

    // 이메일 인증 메일 발송 메서드
    EmailSendResult sendVerificationEmail(String recipientEmail, String verificationCode);

    // 비밀번호 재설정 메일 발송 메서드
    default EmailSendResult sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        throw new UnsupportedOperationException("비밀번호 재설정 메일 발송을 지원하지 않습니다.");
    }
}
