package com.typenull.pingdom.payment.api.dto;

import com.typenull.pingdom.payment.domain.*;
import java.time.LocalDateTime;

public record SettlementLedgerResponse(
        Long id,
        Long paymentTransactionId,
        LedgerEntryType entryType,
        long grossAmountMinor,
        long feeAmountMinor,
        long netAmountMinor,
        String currency,
        SettlementStatus status,
        LocalDateTime createdAt,
        LocalDateTime settledAt
) {
    public static SettlementLedgerResponse from(SettlementLedgerEntry entry) {
        return new SettlementLedgerResponse(entry.getId(), entry.getPaymentTransactionId(), entry.getEntryType(),
                entry.getGrossAmountMinor(), entry.getFeeAmountMinor(), entry.getNetAmountMinor(), entry.getCurrency(),
                entry.getStatus(), entry.getCreatedAt(), entry.getSettledAt());
    }
}
