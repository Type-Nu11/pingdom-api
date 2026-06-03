package com.typenull.pingdom.identity.application.port;

// 인증 메일 발송 추상화 인터페이스
public interface EmailSender {

    // 이메일 인증 메일 발송 메서드
    void sendVerificationEmail(String recipientEmail, String verificationCode);
}
