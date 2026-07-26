package com.typenull.pingdom.payment.api.dto;

import com.typenull.pingdom.payment.domain.PaymentStatus;
import com.typenull.pingdom.payment.domain.PaymentTransaction;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long reservationId,
        String provider,
        String providerPaymentId,
        Long amountMinor,
        String currency,
        PaymentStatus status,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
    public static PaymentResponse from(PaymentTransaction payment) {
        return new PaymentResponse(payment.getId(), payment.getReservationId(), payment.getProvider(),
                payment.getProviderPaymentId(), payment.getAmountMinor(), payment.getCurrency(), payment.getStatus(),
                payment.getFailureCode(), payment.getCreatedAt(), payment.getPaidAt(), payment.getRefundedAt());
    }
}
