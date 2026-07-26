package com.typenull.pingdom.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "payment_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "tourist_user_id", nullable = false)
    private Long touristUserId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_payment_id", length = 150)
    private String providerPaymentId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    private long version;

    public static PaymentTransaction processing(Long reservationId, Long touristUserId, Long merchantOwnerUserId,
            String provider, String idempotencyKey, LocalDateTime now) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        transaction.touristUserId = Objects.requireNonNull(touristUserId, "touristUserId must not be null");
        transaction.merchantOwnerUserId = Objects.requireNonNull(merchantOwnerUserId,
                "merchantOwnerUserId must not be null");
        transaction.provider = requireText(provider, "provider", 30).toUpperCase();
        transaction.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 100);
        transaction.status = PaymentStatus.PROCESSING;
        transaction.createdAt = Objects.requireNonNull(now, "now must not be null");
        transaction.updatedAt = now;
        return transaction;
    }

    public void succeed(String providerPaymentId, long amountMinor, String currency, LocalDateTime now) {
        requireStatus(PaymentStatus.PROCESSING);
        if (amountMinor <= 0) throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        this.providerPaymentId = requireText(providerPaymentId, "providerPaymentId", 150);
        this.amountMinor = amountMinor;
        this.currency = requireCurrency(currency);
        this.status = PaymentStatus.PAID;
        this.paidAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void fail(String failureCode, LocalDateTime now) {
        requireStatus(PaymentStatus.PROCESSING);
        this.failureCode = requireText(failureCode, "failureCode", 50);
        this.status = PaymentStatus.FAILED;
        this.failedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void startRefund(LocalDateTime now) {
        requireStatus(PaymentStatus.PAID);
        this.status = PaymentStatus.REFUND_PROCESSING;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void completeRefund(LocalDateTime now) {
        requireStatus(PaymentStatus.REFUND_PROCESSING);
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void cancelRefund(LocalDateTime now) {
        requireStatus(PaymentStatus.REFUND_PROCESSING);
        this.status = PaymentStatus.PAID;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) throw new IllegalStateException("현재 결제 상태에서는 요청을 처리할 수 없습니다.");
    }

    private static String requireCurrency(String value) {
        String normalized = requireText(value, "currency", 3).toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("통화 코드는 ISO-4217 형식이어야 합니다.");
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
