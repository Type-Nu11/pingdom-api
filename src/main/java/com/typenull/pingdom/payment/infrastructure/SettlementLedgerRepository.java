package com.typenull.pingdom.payment.infrastructure;

import com.typenull.pingdom.payment.domain.LedgerEntryType;
import com.typenull.pingdom.payment.domain.SettlementLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SettlementLedgerRepository extends JpaRepository<SettlementLedgerEntry, Long> {
    boolean existsByPaymentTransactionIdAndEntryType(Long paymentTransactionId, LedgerEntryType entryType);

    Optional<SettlementLedgerEntry> findByPaymentTransactionIdAndEntryType(
            Long paymentTransactionId, LedgerEntryType entryType);

    Page<SettlementLedgerEntry> findAllByMerchantOwnerUserId(Long merchantOwnerUserId, Pageable pageable);
}
