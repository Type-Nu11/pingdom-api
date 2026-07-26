package com.typenull.pingdom.payment.application;

import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.api.dto.PaymentCreateRequest;
import com.typenull.pingdom.payment.api.dto.PaymentResponse;
import com.typenull.pingdom.payment.application.provider.PaymentProviderResult;
import com.typenull.pingdom.payment.domain.*;
import com.typenull.pingdom.payment.domain.exception.*;
import com.typenull.pingdom.payment.infrastructure.*;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLedgerWriter {
    private final PaymentTransactionRepository paymentRepository;
    private final SettlementLedgerRepository ledgerRepository;
    private final ReservationRepository reservationRepository;
    private final PlaceAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PaymentPreparation prepare(Long userId, PaymentCreateRequest request) {
        requireTourist(userRepository.findByIdForUpdate(userId).orElse(null));
        PaymentTransaction existing = paymentRepository
                .findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getReservationId().equals(request.reservationId())
                    || !existing.getProvider().equalsIgnoreCase(request.provider())) {
                throw new PaymentException(PaymentErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new PaymentPreparation(existing.getId(), existing.getReservationId(), existing.getStatus());
        }

        Reservation reservation = reservationRepository.findByIdForUpdate(request.reservationId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.RESERVATION_NOT_PAYABLE));
        if (!reservation.getTouristUserId().equals(userId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new PaymentException(PaymentErrorCode.RESERVATION_NOT_PAYABLE);
        }
        if (paymentRepository.findFirstByReservationIdAndStatusIn(reservation.getId(),
                java.util.List.of(PaymentStatus.PROCESSING, PaymentStatus.PAID)).isPresent()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }
        PlaceAvailability availability = availabilityRepository.findById(reservation.getAvailabilityId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.RESERVATION_NOT_PAYABLE));
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            PaymentTransaction saved = paymentRepository.save(PaymentTransaction.processing(
                    reservation.getId(), userId, availability.getMerchantOwnerUserId(), request.provider(),
                    request.idempotencyKey(), now));
            return new PaymentPreparation(saved.getId(), saved.getReservationId(), saved.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_INPUT);
        }
    }

    @Transactional
    public PaymentResponse complete(Long paymentId, PaymentProviderResult result) {
        PaymentTransaction payment = findForUpdate(paymentId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) return PaymentResponse.from(payment);
        try {
            payment.succeed(result.providerPaymentId(), result.amountMinor(), result.currency(), LocalDateTime.now(clock));
            ledgerRepository.save(SettlementLedgerEntry.payment(payment.getId(), payment.getMerchantOwnerUserId(),
                    result.amountMinor(), result.feeAmountMinor(), result.currency(), LocalDateTime.now(clock)));
            return PaymentResponse.from(payment);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_INPUT);
        }
    }

    @Transactional
    public PaymentResponse fail(Long paymentId, String failureCode) {
        PaymentTransaction payment = findForUpdate(paymentId);
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.fail(normalizeFailureCode(failureCode), LocalDateTime.now(clock));
        }
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse completeRefund(Long ownerId, Long paymentId) {
        PaymentTransaction payment = findForUpdate(paymentId);
        if (!payment.getMerchantOwnerUserId().equals(ownerId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        try {
            payment.completeRefund(LocalDateTime.now(clock));
            if (!ledgerRepository.existsByPaymentTransactionIdAndEntryType(paymentId, LedgerEntryType.REFUND)) {
                SettlementLedgerEntry paymentEntry = ledgerRepository
                        .findByPaymentTransactionIdAndEntryType(paymentId, LedgerEntryType.PAYMENT)
                        .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATE));
                ledgerRepository.save(SettlementLedgerEntry.refund(paymentId, ownerId, payment.getAmountMinor(),
                        paymentEntry.getFeeAmountMinor(),
                        payment.getCurrency(), LocalDateTime.now(clock)));
            }
            return PaymentResponse.from(payment);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATE);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long paymentId) {
        return PaymentResponse.from(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND)));
    }

    @Transactional
    public PaymentResponse prepareRefund(Long ownerId, Long paymentId) {
        PaymentTransaction payment = findForUpdate(paymentId);
        if (!payment.getMerchantOwnerUserId().equals(ownerId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        try {
            payment.startRefund(LocalDateTime.now(clock));
            return PaymentResponse.from(payment);
        } catch (IllegalStateException exception) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATE);
        }
    }

    @Transactional
    public void cancelRefund(Long ownerId, Long paymentId) {
        PaymentTransaction payment = findForUpdate(paymentId);
        if (!payment.getMerchantOwnerUserId().equals(ownerId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        try {
            payment.cancelRefund(LocalDateTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATE);
        }
    }

    private PaymentTransaction findForUpdate(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private void requireTourist(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
    }

    private String normalizeFailureCode(String value) {
        if (value == null || value.isBlank()) return "PROVIDER_ERROR";
        String normalized = value.trim().toUpperCase();
        return normalized.substring(0, Math.min(normalized.length(), 50));
    }
}
