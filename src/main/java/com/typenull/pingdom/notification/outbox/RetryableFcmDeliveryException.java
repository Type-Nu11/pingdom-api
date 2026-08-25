package com.typenull.pingdom.notification.outbox;

/** 영속화된 FCM 임시 실패를 기존 Outbox 재시도 정책으로 회수하기 위한 예외입니다. */
public class RetryableFcmDeliveryException extends RuntimeException {

    public RetryableFcmDeliveryException(String eventId) {
        super("재시도 가능한 FCM 전송 실패가 남아 있습니다. eventId=" + eventId);
    }
}
