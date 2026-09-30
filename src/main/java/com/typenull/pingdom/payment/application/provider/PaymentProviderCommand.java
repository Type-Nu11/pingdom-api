package com.typenull.pingdom.payment.application.provider;

/** 견적 예약은 기대 금액·통화로 승인해야 함. 둘 다 null이면 기존 견적 없는 예약 계약. */
public record PaymentProviderCommand(
        Long paymentTransactionId,
        Long reservationId,
        String paymentToken,
        String idempotencyKey,
        Long expectedAmountMinor,
        String expectedCurrency
) {
    public PaymentProviderCommand(Long paymentTransactionId, Long reservationId, String paymentToken, String idempotencyKey) {
        this(paymentTransactionId, reservationId, paymentToken, idempotencyKey, null, null);
    }
}
