package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.api.dto.*;
import com.typenull.pingdom.payment.domain.PaymentTransaction;
import com.typenull.pingdom.payment.domain.exception.*;
import com.typenull.pingdom.payment.infrastructure.*;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {
    private final PaymentTransactionRepository paymentRepository;
    private final SettlementLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final AvailabilityAccessPolicy availabilityAccessPolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PaymentResponse getMine(Long userId, Long paymentId) {
        requireTourist(userId);
        PaymentTransaction payment = find(paymentId);
        if (!payment.getTouristUserId().equals(userId)) throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        return paymentPage(paymentRepository.findAllByTouristUserId(userId, pageRequest(page, limit)), page, limit);
    }

    @Transactional(readOnly = true)
    public PaymentPageResponse listOwned(Long ownerId, int page, int limit) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return paymentPage(paymentRepository.findAllByMerchantOwnerUserId(ownerId, pageRequest(page, limit)),
                page, limit);
    }

    @Transactional(readOnly = true)
    public SettlementLedgerPageResponse listSettlementLedger(Long ownerId, int page, int limit) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        Page<com.typenull.pingdom.payment.domain.SettlementLedgerEntry> entries =
                ledgerRepository.findAllByMerchantOwnerUserId(ownerId, pageRequest(page, limit));
        return new SettlementLedgerPageResponse(entries.getContent().stream()
                .map(SettlementLedgerResponse::from).toList(), page, limit, entries.getTotalElements(),
                entries.getTotalPages(), entries.hasNext());
    }

    private PaymentPageResponse paymentPage(Page<PaymentTransaction> payments, int page, int limit) {
        return new PaymentPageResponse(payments.getContent().stream().map(PaymentResponse::from).toList(),
                page, limit, payments.getTotalElements(), payments.getTotalPages(), payments.hasNext());
    }

    private PaymentTransaction find(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
    }
}
