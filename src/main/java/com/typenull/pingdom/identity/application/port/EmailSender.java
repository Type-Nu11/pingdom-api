package com.typenull.pingdom.identity.application.port;

import java.time.LocalDateTime;

// 인증 메일 발송 추상화 인터페이스
public interface EmailSender {

    // 이메일 인증 메일 발송 메서드
    EmailSendResult sendVerificationEmail(String recipientEmail, String verificationCode);

    // 비밀번호 재설정 메일 발송 메서드
    default EmailSendResult sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        throw new UnsupportedOperationException("비밀번호 재설정 메일 발송을 지원하지 않습니다.");
    }
}
