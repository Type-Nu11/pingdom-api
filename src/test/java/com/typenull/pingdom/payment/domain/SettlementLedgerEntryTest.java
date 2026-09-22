package com.typenull.pingdom.payment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementLedgerEntryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

    /**
     * 총액 10,000·수수료 300의 결제 원장이 순액 9,700과 PENDING 상태를 기록하는지 검증.
     */
    @Test
    void paymentEntryCalculatesNetAmount() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.payment(1L, 2L, 10_000L, 300L, "KRW", now);

        assertThat(entry.getGrossAmountMinor()).isEqualTo(10_000L);
        assertThat(entry.getFeeAmountMinor()).isEqualTo(300L);
        assertThat(entry.getNetAmountMinor()).isEqualTo(9_700L);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    /**
     * 환불 원장은 총액·수수료·순액의 부호를 모두 반전하고 REVERSED 상태로 생성되는지 검증.
     */
    @Test
    void refundEntryReversesAllAmounts() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.refund(1L, 2L, 10_000L, 300L, "KRW", now);

        assertThat(entry.getGrossAmountMinor()).isEqualTo(-10_000L);
        assertThat(entry.getFeeAmountMinor()).isEqualTo(-300L);
        assertThat(entry.getNetAmountMinor()).isEqualTo(-9_700L);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.REVERSED);
    }

    /**
     * 총액 100보다 큰 수수료 101을 지정하면 IllegalArgumentException이 발생하는지 검증.
     */
    @Test
    void feeCannotExceedGrossAmount() {
        assertThatThrownBy(() -> SettlementLedgerEntry.payment(1L, 2L, 100L, 101L, "KRW", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 정산 후 SETTLED 상태를 확인하고 재정산이 IllegalStateException으로 거절되는지 검증.
     */
    @Test
    void settledEntryCannotSettleAgain() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.payment(1L, 2L, 100L, 1L, "KRW", now);
        entry.settle(now.plusDays(1));

        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThatThrownBy(() -> entry.settle(now.plusDays(2))).isInstanceOf(IllegalStateException.class);
    }
}
