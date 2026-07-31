package com.typenull.pingdom.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "settlement_ledger_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_transaction_id", nullable = false)
    private Long paymentTransactionId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Column(name = "gross_amount_minor", nullable = false)
    private long grossAmountMinor;

    @Column(name = "fee_amount_minor", nullable = false)
    private long feeAmountMinor;

    @Column(name = "net_amount_minor", nullable = false)
    private long netAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    public static SettlementLedgerEntry payment(Long paymentTransactionId, Long merchantOwnerUserId,
            long grossAmountMinor, long feeAmountMinor, String currency, LocalDateTime now) {
        validateAmounts(grossAmountMinor, feeAmountMinor);
        SettlementLedgerEntry entry = base(paymentTransactionId, merchantOwnerUserId, currency, now);
        entry.entryType = LedgerEntryType.PAYMENT;
        entry.grossAmountMinor = grossAmountMinor;
        entry.feeAmountMinor = feeAmountMinor;
        entry.netAmountMinor = grossAmountMinor - feeAmountMinor;
        entry.status = SettlementStatus.PENDING;
        return entry;
    }

    public static SettlementLedgerEntry refund(Long paymentTransactionId, Long merchantOwnerUserId,
            long grossAmountMinor, long feeAmountMinor, String currency, LocalDateTime now) {
        validateAmounts(grossAmountMinor, feeAmountMinor);
        SettlementLedgerEntry entry = base(paymentTransactionId, merchantOwnerUserId, currency, now);
        entry.entryType = LedgerEntryType.REFUND;
        entry.grossAmountMinor = -grossAmountMinor;
        entry.feeAmountMinor = -feeAmountMinor;
        entry.netAmountMinor = -(grossAmountMinor - feeAmountMinor);
        entry.status = SettlementStatus.REVERSED;
        return entry;
    }

    public void settle(LocalDateTime now) {
        if (status != SettlementStatus.PENDING) throw new IllegalStateException("정산 대기 원장만 정산할 수 있습니다.");
        status = SettlementStatus.SETTLED;
        settledAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static SettlementLedgerEntry base(Long paymentTransactionId, Long merchantOwnerUserId,
            String currency, LocalDateTime now) {
        SettlementLedgerEntry entry = new SettlementLedgerEntry();
        entry.paymentTransactionId = Objects.requireNonNull(paymentTransactionId,
                "paymentTransactionId must not be null");
        entry.merchantOwnerUserId = Objects.requireNonNull(merchantOwnerUserId,
                "merchantOwnerUserId must not be null");
        if (currency == null || !currency.toUpperCase().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("통화 코드는 ISO-4217 형식이어야 합니다.");
        }
        entry.currency = currency.toUpperCase();
        entry.createdAt = Objects.requireNonNull(now, "now must not be null");
        return entry;
    }

    private static void validateAmounts(long grossAmountMinor, long feeAmountMinor) {
        if (grossAmountMinor <= 0 || feeAmountMinor < 0 || feeAmountMinor > grossAmountMinor) {
            throw new IllegalArgumentException("정산 금액 구성이 올바르지 않습니다.");
        }
    }
}
