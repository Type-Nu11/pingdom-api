package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.api.dto.fcm.NotificationResponse;

/** FCM 토큰 전송을 마친 뒤 Outbox 재시도 필요 여부를 전달합니다. */
public record FcmDispatchResult(
        NotificationResponse notification,
        boolean hasRetryableFailure
) {

    public static FcmDispatchResult skipped() {
        return new FcmDispatchResult(null, false);
    }
}
