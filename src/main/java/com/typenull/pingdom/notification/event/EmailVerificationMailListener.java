package com.typenull.pingdom.notification.event;

import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.event.EmailVerificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailVerificationMailListener {

    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(EmailVerificationRequestedEvent event) {
        emailSender.sendVerificationEmail(event.recipientEmail(), event.verificationCode());
    }
}
