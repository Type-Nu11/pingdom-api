package com.typenull.pingdom.notification.outbox;

/** FCM 서비스가 보고한 임시 실패를 Outbox 재시도로 연결. 발송 결과 기록 성공 여부의 보장은 범위 외. */
public class RetryableFcmDeliveryException extends RuntimeException {

    public RetryableFcmDeliveryException(String eventId) {
        super("재시도 가능한 FCM 전송 실패가 남아 있습니다. eventId=" + eventId);
    }
}
