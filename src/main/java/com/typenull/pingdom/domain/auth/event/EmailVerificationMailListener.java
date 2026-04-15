package com.typenull.pingdom.domain.auth.event;

import com.typenull.pingdom.domain.auth.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 트랜잭션 커밋 후 인증 메일 발송 처리 리스너
@Component
@RequiredArgsConstructor
public class EmailVerificationMailListener {

    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // 인증 메일 발송 이벤트 처리 메서드
    public void handle(EmailVerificationRequestedEvent event) {
        emailSender.sendVerificationEmail(event.recipientEmail(), event.verificationCode());
    }
}
