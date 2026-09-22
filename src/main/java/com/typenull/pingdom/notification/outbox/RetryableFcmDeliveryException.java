package com.typenull.pingdom.notification.outbox;

/** FCM 서비스가 보고한 임시 실패를 Outbox 재시도로 연결합니다. 발송 결과 기록 성공 여부 자체를 보장하는 예외는 아닙니다. */
public class RetryableFcmDeliveryException extends RuntimeException {

    public RetryableFcmDeliveryException(String eventId) {
        super("재시도 가능한 FCM 전송 실패가 남아 있습니다. eventId=" + eventId);
    }
}
