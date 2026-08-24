package com.typenull.pingdom.payment.api.dto;

import com.typenull.pingdom.payment.domain.PaymentStatus;
import com.typenull.pingdom.payment.domain.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관광객 결제 응답")
public record PaymentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long reservationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String provider,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 PAID 상태가 되기 전에는 null입니다."
        )
        String providerPaymentId,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 성공하기 전에는 null입니다."
        )
        Long amountMinor,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 성공하기 전에는 null입니다."
        )
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentStatus status,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 FAILED 상태일 때만 제공됩니다."
        )
        String failureCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 PAID, REFUND_PROCESSING 또는 REFUNDED 상태가 되기 전에는 null입니다."
        )
        LocalDateTime paidAt,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 FAILED 상태일 때만 제공됩니다."
        )
        LocalDateTime failedAt,
        @Schema(
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "결제가 REFUNDED 상태가 되기 전에는 null입니다."
        )
        LocalDateTime refundedAt
) {
    public static PaymentResponse from(PaymentTransaction payment) {
        return new PaymentResponse(payment.getId(), payment.getReservationId(), payment.getProvider(),
                payment.getProviderPaymentId(), payment.getAmountMinor(), payment.getCurrency(), payment.getStatus(),
                payment.getFailureCode(), payment.getCreatedAt(), payment.getPaidAt(), payment.getFailedAt(),
                payment.getRefundedAt());
    }
}
