package com.typenull.pingdom.payment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementLedgerEntryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Test
    void paymentEntryCalculatesNetAmount() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.payment(1L, 2L, 10_000L, 300L, "KRW", now);

        assertThat(entry.getGrossAmountMinor()).isEqualTo(10_000L);
        assertThat(entry.getFeeAmountMinor()).isEqualTo(300L);
        assertThat(entry.getNetAmountMinor()).isEqualTo(9_700L);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    void refundEntryReversesAllAmounts() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.refund(1L, 2L, 10_000L, 300L, "KRW", now);

        assertThat(entry.getGrossAmountMinor()).isEqualTo(-10_000L);
        assertThat(entry.getFeeAmountMinor()).isEqualTo(-300L);
        assertThat(entry.getNetAmountMinor()).isEqualTo(-9_700L);
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.REVERSED);
    }

    @Test
    void feeCannotExceedGrossAmount() {
        assertThatThrownBy(() -> SettlementLedgerEntry.payment(1L, 2L, 100L, 101L, "KRW", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settledEntryCannotSettleAgain() {
        SettlementLedgerEntry entry = SettlementLedgerEntry.payment(1L, 2L, 100L, 1L, "KRW", now);
        entry.settle(now.plusDays(1));

        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThatThrownBy(() -> entry.settle(now.plusDays(2))).isInstanceOf(IllegalStateException.class);
    }
}
