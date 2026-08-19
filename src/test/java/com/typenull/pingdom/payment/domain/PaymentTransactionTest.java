package com.typenull.pingdom.payment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 결제 성공·실패·환불 상태 전이의 도메인 경계를 검증합니다. */
class PaymentTransactionTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Test
    void processingPaymentCanSucceedWithProviderAmount() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);

        payment.succeed("provider-payment-1", 10_000L, "krw", now.plusMinutes(1));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountMinor()).isEqualTo(10_000L);
        assertThat(payment.getCurrency()).isEqualTo("KRW");
        assertThat(payment.getProvider()).isEqualTo("PROVIDER");
    }

    @Test
    void paidPaymentCanBeRefundedOnlyOnce() {
        PaymentTransaction payment = paidPayment();

        payment.startRefund(now.plusMinutes(2));
        payment.completeRefund(now.plusMinutes(3));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> payment.startRefund(now.plusMinutes(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void declinedRefundCanReturnToPaid() {
        PaymentTransaction payment = paidPayment();
        payment.startRefund(now.plusMinutes(2));

        payment.cancelRefund(now.plusMinutes(3));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAt()).isNull();
    }

    @Test
    void invalidProviderAmountIsRejected() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);

        assertThatThrownBy(() -> payment.succeed("provider-payment-1", 0, "KRW", now.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedPaymentCannotSucceedLater() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);
        payment.fail("DECLINED", now.plusMinutes(1));

        assertThatThrownBy(() -> payment.succeed("provider-payment-1", 10_000L, "KRW", now.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private PaymentTransaction paidPayment() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);
        payment.succeed("provider-payment-1", 10_000L, "KRW", now.plusMinutes(1));
        return payment;
    }
}
