package com.typenull.pingdom.identity.application.port;

import java.time.LocalDateTime;

/**
 * 인증·비밀번호 복구 메일을 보내는 외부 발송 포트.
 * 어댑터는 인증 메일과 비밀번호 재설정 메일 발송을 모두 지원해야 합니다.
 */
// 인증 메일 발송 추상화 인터페이스
public interface EmailSender {

    // 이메일 인증 메일 발송 메서드
    EmailSendResult sendVerificationEmail(String recipientEmail, String verificationCode);

    // 비밀번호 재설정 메일 발송 메서드
    EmailSendResult sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt);
}
