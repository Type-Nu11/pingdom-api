package com.typenull.pingdom.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.availability.domain.*;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.domain.PaymentStatus;
import com.typenull.pingdom.payment.infrastructure.PaymentTransactionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.domain.ReservableProductStatus;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationQuoteRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode.*;

/** 견적 준비와 생성 전 검증. 생성 검증은 예약 트랜잭션 안에서만 실행하며 재고 변경 전에 거절을 확정. */
@Service
@RequiredArgsConstructor
public class ReservationQuoteService {
    private static final int HOURLY_QUOTE_LIMIT = 60;
    private static final int UNUSED_QUOTE_LIMIT = 120;
    private static final int CLEANUP_BATCH_LIMIT = 20;
    private static final long CLEANUP_BUDGET_NANOS = Duration.ofSeconds(2).toNanos();
    private final ReservationQuoteRepository quotes;
    private final PlaceAvailabilityRepository availabilities;
    private final ReservableProductRepository products;
    private final MapPlaceRepository places;
    private final UserRepository users;
    private final PaymentTransactionRepository payments;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public ReservationQuoteResponse issue(Long userId, Long placeId, Long availabilityId, int quantity) {
        requireTourist(users.findByIdForUpdate(userId).orElse(null));
        // 사용자 잠금 아래 검사하여 여러 인스턴스의 동시 발급도 같은 한도를 적용.
        if (quotes.countByTouristUserIdAndCreatedAtAfter(userId, clock.instant().minusSeconds(3600)) >= HOURLY_QUOTE_LIMIT
                || quotes.countByTouristUserIdAndIdempotencyKeyIsNull(userId) >= UNUSED_QUOTE_LIMIT) {
            throw new ReservationException(QUOTE_RATE_LIMITED);
        }
        PlaceAvailability slot = availabilities.findByIdForUpdate(availabilityId)
                .filter(value -> value.getPlaceId().equals(placeId))
                .orElseThrow(() -> new ReservationException(RESERVATION_SLOT_NOT_FOUND));
        ReservationErrorCode error = slotError(slot, quantity);
        if (error != null) throw new ReservationException(error);
        ReservableProduct product = product(slot);
        if (!validProduct(slot, product)) throw new ReservationException(RESERVATION_PRODUCT_UNAVAILABLE);
        if (availabilities.findReservableByIdForUpdate(availabilityId, LocalDateTime.now(clock)).isEmpty()) {
            throw new ReservationException(RESERVATION_SLOT_INACTIVE);
        }
        Instant expiration = clock.instant().plusSeconds(300);
        Instant startsAt = slot.getStartsAt().atZone(clock.getZone()).toInstant();
        if (startsAt.isBefore(expiration)) expiration = startsAt;
        ReservationConfirmation confirmation = snapshot(slot, product, quantity, expiration);
        ReservationQuote quote = quotes.save(ReservationQuote.issue(userId, confirmation, clock.instant()));
        return ReservationQuoteResponse.from(quote, slot.getRemainingCapacity());
    }

    /** 미사용 견적만 하루 뒤 정리. 배치별 커밋으로 잠금과 롤백 범위를 제한하며 적체를 여러 배치로 처리. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${reservation.quote.cleanup-delay-ms:60000}",
            initialDelayString = "${reservation.quote.cleanup-delay-ms:60000}")
    public void removeUnusedExpiredQuotes() {
        Instant cutoff = clock.instant().minusSeconds(86400);
        long startedAt = System.nanoTime();
        for (int batch = 0; batch < CLEANUP_BATCH_LIMIT && System.nanoTime() - startedAt < CLEANUP_BUDGET_NANOS; batch++) {
            if (quotes.deleteUnusedBefore(cutoff) < 1000) break;
        }
    }

    /** 사용자 잠금 이후 호출. 실패 키를 토큰 없이 재사용하는 구버전 요청도 거절. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ConfirmedReservationRejectedException.class)
    public void checkPriorResult(Long userId, ReservationCreateRequest request) {
        ReservationQuote prior = quotes.findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey())
                .orElse(null);
        if (prior == null) return;
        if (!Objects.equals(prior.getId(), request.confirmationToken())
                || !Objects.equals(prior.getRequestFingerprint(), fingerprint(request))) {
            throw new ReservationException(IDEMPOTENCY_KEY_REUSED);
        }
        if (prior.getRejectionCode() != null) {
            throw new ConfirmedReservationRejectedException(prior.getRejectionCode());
        }
    }

    /** 호출자의 예약 트랜잭션에만 참여하며 확정 거절은 rollback-only로 표시하지 않아 영수증을 보존. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ConfirmedReservationRejectedException.class)
    public ReservationConfirmation verify(Long userId, ReservationCreateRequest request) {
        if (request.confirmationToken() == null) return null;
        ReservationQuote quote = quotes.findByIdForUpdate(request.confirmationToken())
                .filter(value -> value.getTouristUserId().equals(userId))
                .orElseThrow(() -> new ReservationException(QUOTE_NOT_FOUND));
        String fingerprint = fingerprint(request);
        if (quote.getIdempotencyKey() != null
                && (!quote.getIdempotencyKey().equals(request.idempotencyKey())
                || !quote.getRequestFingerprint().equals(fingerprint))) {
            throw new ReservationException(IDEMPOTENCY_KEY_REUSED);
        }
        if (quote.getRejectionCode() != null) throw new ConfirmedReservationRejectedException(quote.getRejectionCode());
        quote.bind(request.idempotencyKey(), fingerprint);
        ReservationConfirmation confirmed = quote.getConfirmation();
        if (!confirmed.availabilityId().equals(request.availabilityId()) || confirmed.quantity() != request.quantity()) {
            reject(quote, QUOTE_REQUEST_MISMATCH);
        }
        if (!clock.instant().isBefore(confirmed.expiresAt())) reject(quote, QUOTE_EXPIRED);
        PlaceAvailability slot = availabilities.findByIdForUpdate(request.availabilityId()).orElse(null);
        if (slot == null) reject(quote, RESERVATION_SLOT_NOT_FOUND);
        ReservationErrorCode error = slotError(slot, request.quantity());
        if (error != null) reject(quote, error);
        ReservableProduct product = product(slot);
        if (!validProduct(slot, product)) reject(quote, RESERVATION_PRODUCT_UNAVAILABLE);
        if (availabilities.findReservableByIdForUpdate(slot.getId(), LocalDateTime.now(clock)).isEmpty()) {
            reject(quote, RESERVATION_SLOT_INACTIVE);
        }
        if (slot.getReservationTerms() == null) reject(quote, QUOTE_TERMS_UNAVAILABLE);
        try {
            if (!confirmed.equals(snapshot(slot, product, request.quantity(), confirmed.expiresAt()))) {
                reject(quote, QUOTE_CONDITIONS_CHANGED);
            }
        } catch (ReservationException exception) {
            if (exception instanceof ConfirmedReservationRejectedException) throw exception;
            reject(quote, (ReservationErrorCode) exception.getErrorCode());
        }
        // 슬롯·상품 잠금 및 조건 조회 대기 중 만료된 토큰으로 재고를 차감하지 않음.
        if (!clock.instant().isBefore(confirmed.expiresAt())) reject(quote, QUOTE_EXPIRED);
        return confirmed;
    }

    public void requireCancellationAllowed(Reservation reservation) {
        ReservationConfirmation confirmation = reservation.getConfirmation();
        if (confirmation == null) return;
        if (!confirmation.cancellable() || !clock.instant().isBefore(confirmation.cancellationDeadline().toInstant())) {
            throw new ReservationException(CANCELLATION_NOT_ALLOWED);
        }
        // 공급자 환불은 별도의 명시적 동작. 처리 중 결제나 미환불 결제를 취소 성공으로 노출하지 않음.
        if (payments.findFirstByReservationIdAndStatusIn(reservation.getId(),
                List.of(PaymentStatus.PROCESSING, PaymentStatus.PAID, PaymentStatus.REFUND_PROCESSING)).isPresent()) {
            throw new ReservationException(RESERVATION_REFUND_REQUIRED);
        }
    }

    private void reject(ReservationQuote quote, ReservationErrorCode code) {
        quote.reject(code);
        throw new ConfirmedReservationRejectedException(code);
    }

    private ReservationErrorCode slotError(PlaceAvailability slot, int quantity) {
        if (slot.getStatus() != AvailabilityStatus.ACTIVE || !slot.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            return RESERVATION_SLOT_INACTIVE;
        }
        if (quantity < 1) return INVALID_RESERVATION_INPUT;
        if (slot.getRemainingCapacity() < quantity) return RESERVATION_CAPACITY_EXCEEDED;
        return null;
    }

    private ReservableProduct product(PlaceAvailability slot) {
        return slot.getProductId() == null ? null : products.findByIdForUpdate(slot.getProductId()).orElse(null);
    }

    private boolean validProduct(PlaceAvailability slot, ReservableProduct product) {
        if (slot.getProductType() == AvailabilityProductType.GENERAL) return slot.getProductId() == null;
        return product != null && product.getStatus() == ReservableProductStatus.ACTIVE
                && product.getPlaceId().equals(slot.getPlaceId()) && product.getProductType() == slot.getProductType();
    }

    private ReservationConfirmation snapshot(PlaceAvailability slot, ReservableProduct product, int quantity, Instant expiration) {
        ReservationTerms terms = slot.getReservationTerms();
        if (terms == null) throw new ReservationException(QUOTE_TERMS_UNAVAILABLE);
        var place = places.findById(slot.getPlaceId()).orElseThrow(() -> new ReservationException(RESERVATION_SLOT_NOT_FOUND));
        try {
            ZoneId timezone = ZoneId.of(terms.timezone());
            // 기존 LocalDateTime은 주입 Clock 시간대 기준. 표시 timezone을 바꿔도 예약 순간은 재해석하지 않음.
            if (clock.getZone().getRules().getValidOffsets(slot.getStartsAt()).size() != 1
                    || clock.getZone().getRules().getValidOffsets(slot.getEndsAt()).size() != 1) {
                throw new ReservationException(QUOTE_TERMS_UNAVAILABLE);
            }
            OffsetDateTime start = slot.getStartsAt().atZone(clock.getZone()).withZoneSameInstant(timezone).toOffsetDateTime();
            OffsetDateTime end = slot.getEndsAt().atZone(clock.getZone()).withZoneSameInstant(timezone).toOffsetDateTime();
            long total = terms.totalAmountMinor(quantity);
            return new ReservationConfirmation(slot.getPlaceId(), place.getName(), slot.getId(), slot.getProductType(),
                    slot.getProductId(), product == null ? null : product.getName(), start, end, quantity,
                    terms.timezone(), terms.unitAmountMinor(), terms.additionalAmountMinor(), total, terms.currency(),
                    Currency.getInstance(terms.currency()).getDefaultFractionDigits(), total > 0, terms.cancellable(),
                    terms.cancellable() ? start.minusMinutes(terms.cancellationCutoffMinutes()) : null,
                    0, terms.cancellable() ? total : 0, slot.getConditionsVersion(),
                    product == null ? null : product.getVersion(), expiration);
        } catch (ArithmeticException | DateTimeException exception) {
            throw new ReservationException(QUOTE_TERMS_UNAVAILABLE);
        }
    }

    private String fingerprint(ReservationCreateRequest request) {
        try {
            // 구조적 직렬화로 구분자 충돌 방지. 기존 예약과 동일하게 공백을 정규화.
            byte[] bytes = objectMapper.writeValueAsBytes(Arrays.asList(request.availabilityId(), request.quantity(),
                    request.bookerName().trim(), request.bookerPhone().trim(), normalize(request.requestNote()),
                    request.confirmationToken()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("예약 요청 지문을 계산할 수 없습니다.", exception);
        }
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void requireTourist(User user) {
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn()
                || user.isCurrentlyBanned(LocalDateTime.now(clock))) {
            throw new ReservationException(TOURIST_ACCOUNT_REQUIRED);
        }
    }
}
