package com.typenull.pingdom.payment.application.provider;

public record PaymentProviderCommand(
        Long paymentTransactionId,
        Long reservationId,
        String paymentToken,
        String idempotencyKey
) {}
