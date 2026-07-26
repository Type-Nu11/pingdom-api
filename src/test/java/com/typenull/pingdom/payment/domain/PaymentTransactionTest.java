package com.typenull.pingdom.payment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

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

        payment.refund(now.plusMinutes(2));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> payment.refund(now.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class);
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
