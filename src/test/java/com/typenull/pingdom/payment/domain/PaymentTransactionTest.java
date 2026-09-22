package com.typenull.pingdom.payment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 결제 성공·실패·환불 상태 전이의 도메인 경계를 검증. */
class PaymentTransactionTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

    /**
     * 처리 중 결제가 성공하면 PAID와 결제 금액을 기록하고 통화·공급자명을 대문자로 정규화하는지 검증.
     */
    @Test
    void acceptsProviderPaymentAmount() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);

        payment.succeed("provider-payment-1", 10_000L, "krw", now.plusMinutes(1));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountMinor()).isEqualTo(10_000L);
        assertThat(payment.getCurrency()).isEqualTo("KRW");
        assertThat(payment.getProvider()).isEqualTo("PROVIDER");
    }

    /**
     * 결제의 환불 시작·완료 후 REFUNDED 상태이며 재환불에는 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void refundsPaidPaymentOnce() {
        PaymentTransaction payment = paidPayment();

        payment.startRefund(now.plusMinutes(2));
        payment.completeRefund(now.plusMinutes(3));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> payment.startRefund(now.plusMinutes(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 진행 중 환불을 취소하면 PAID 상태로 복원되고 환불 완료 시각이 남지 않는지 검증.
     */
    @Test
    void declinedRefundReturnsToPaid() {
        PaymentTransaction payment = paidPayment();
        payment.startRefund(now.plusMinutes(2));

        payment.cancelRefund(now.plusMinutes(3));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAt()).isNull();
    }

    /**
     * 공급자 결제 금액 0으로 성공 처리하면 IllegalArgumentException이 발생하는지 검증.
     */
    @Test
    void invalidProviderAmountIsRejected() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);

        assertThatThrownBy(() -> payment.succeed("provider-payment-1", 0, "KRW", now.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * DECLINED로 실패 처리한 결제는 후속 성공 요청을 IllegalStateException으로 거절하는지 검증.
     */
    @Test
    void failedPaymentCannotSucceedLater() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);
        payment.fail("DECLINED", now.plusMinutes(1));

        assertThatThrownBy(() -> payment.succeed("provider-payment-1", 10_000L, "KRW", now.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 처리 중 결제를 10,000 KRW로 성공시켜 환불 테스트가 PAID 상태에서 시작하도록 구성.
     */
    private PaymentTransaction paidPayment() {
        PaymentTransaction payment = PaymentTransaction.processing(1L, 2L, 3L, "provider", "key", now);
        payment.succeed("provider-payment-1", 10_000L, "KRW", now.plusMinutes(1));
        return payment;
    }
}
