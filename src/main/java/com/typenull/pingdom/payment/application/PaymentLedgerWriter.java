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

/**
 * 결제와 정산 원장의 DB 상태를 짧은 트랜잭션 단위로 기록.
 * 결제 준비는 사용자·예약을, 결과 반영은 결제 행을 잠그며 외부 승인·환불 호출은 담당 범위 외.
 */
@Service
@RequiredArgsConstructor
public class PaymentLedgerWriter {
    private final PaymentTransactionRepository paymentRepository;
    private final SettlementLedgerRepository ledgerRepository;
    private final ReservationRepository reservationRepository;
    private final PlaceAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 사용자별 멱등 키의 예약·사업자가 같은지 검사하고 신규 요청이면 PROCESSING 거래를 생성.
     * 예약별 PROCESSING 또는 PAID 거래가 이미 있으면 거절. 결제 가능성은 아래 명시된 상태 검사 범위로 제한됨.
     */
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

    /**
     * PROCESSING 거래에만 사업자 금액·통화를 반영하고 PAYMENT 원장을 같은 트랜잭션에서 기록.
     * 이미 처리된 거래는 외부 사업자 결과와의 재대조 없이 현재 결과를 반환.
     */
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

    /**
     * 소유 점주의 환불 상태를 완료로 바꾸고 원 결제 수수료를 포함한 역분개를 기록.
     * DB 반영 실패 시 이 트랜잭션은 롤백하되 이미 실행된 외부 환불은 유지.
     */
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

    /**
     * 결제 행을 잠가 기록된 점주와 요청자가 같은지 확인하고 PAID 결제를 REFUND_PROCESSING으로 바꿔 반환.
     * 결제 부재·타인 결제·불가능한 상태는 거절하며 외부 공급자 환불 호출은 명령 서비스가 이후 수행.
     */
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

    /**
     * 공급자가 환불을 거절한 경로에서 결제 행을 잠가 소유 점주를 확인하고 REFUND_PROCESSING을 PAID로 되돌림.
     * 결제 부재·권한·상태 오류는 거절하며 외부 환불 취소와 정산 원장 기록은 이 메서드의 처리 범위 외.
     */
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
