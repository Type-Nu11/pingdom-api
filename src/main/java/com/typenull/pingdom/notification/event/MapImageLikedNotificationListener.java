package com.typenull.pingdom.notification.event;

import com.typenull.pingdom.engagement.event.MapImageLikedEvent;
import com.typenull.pingdom.notification.application.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MapImageLikedNotificationListener {

    private final FcmService fcmService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MapImageLikedEvent event) {
        fcmService.sendLikeNotification(event.ownerId(), event.likerId());
    }
}
