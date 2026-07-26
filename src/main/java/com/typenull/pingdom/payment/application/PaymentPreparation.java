package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.payment.domain.PaymentStatus;

record PaymentPreparation(Long paymentId, Long reservationId, PaymentStatus status) {
    boolean requiresProviderCall() {
        return status == PaymentStatus.PROCESSING;
    }
}
