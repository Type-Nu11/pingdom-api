package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.payment.domain.PaymentStatus;

record PaymentPreparation(Long paymentId, Long reservationId, PaymentStatus status,
        Long expectedAmountMinor, String expectedCurrency) {
    PaymentPreparation(Long paymentId, Long reservationId, PaymentStatus status) {
        this(paymentId, reservationId, status, null, null);
    }
    boolean requiresProviderCall() {
        return status == PaymentStatus.PROCESSING;
    }
}
