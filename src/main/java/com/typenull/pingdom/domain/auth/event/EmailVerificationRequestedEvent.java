package com.typenull.pingdom.domain.auth.event;

// 이메일 인증 메일 발송 요청 이벤트
public record EmailVerificationRequestedEvent(
        String recipientEmail,
        String verificationCode
) {
}
