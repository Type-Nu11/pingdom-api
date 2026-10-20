package com.typenull.pingdom.reservation.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import com.typenull.pingdom.reservation.infrastructure.ReservationStatusHistoryRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광객 예약과 상점주 심사를 가용 재고·상태 이력에 연결하고 관리자에게 조회를 제공.
 * 생성은 사용자 잠금으로 멱등 키를 검사하고, 상태 변경은 예약 잠금으로 직렬화. 결제 승인 검증은 처리 범위 외.
 */
@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final PlaceAvailabilityRepository availabilityRepository;
    private final PlaceAvailabilityService availabilityService;
    private final AvailabilityAccessPolicy availabilityAccessPolicy;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceConversionEventService conversionEventService;
    private final Clock clock;
    private final ReservationQuoteService quoteService;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
            ReservationStatusHistoryRepository reservationStatusHistoryRepository,
            PlaceAvailabilityRepository availabilityRepository, PlaceAvailabilityService availabilityService,
            AvailabilityAccessPolicy availabilityAccessPolicy, MerchantOwnerPlaceRepository ownerPlaceRepository,
            UserRepository userRepository, MapPlaceRepository mapPlaceRepository,
            PlaceConversionEventService conversionEventService, Clock clock, ReservationQuoteService quoteService) {
        this.reservationRepository = reservationRepository;
        this.reservationStatusHistoryRepository = reservationStatusHistoryRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityService = availabilityService;
        this.availabilityAccessPolicy = availabilityAccessPolicy;
        this.ownerPlaceRepository = ownerPlaceRepository;
        this.userRepository = userRepository;
        this.mapPlaceRepository = mapPlaceRepository;
        this.conversionEventService = conversionEventService;
        this.clock = clock;
        this.quoteService = quoteService;
    }

    /** 기존 단위 테스트와 내부 호출의 생성자 호환을 위한 보조 생성자. */
    @Deprecated
    public ReservationService(ReservationRepository reservationRepository,
            PlaceAvailabilityRepository availabilityRepository, PlaceAvailabilityService availabilityService,
            AvailabilityAccessPolicy availabilityAccessPolicy, MerchantOwnerPlaceRepository ownerPlaceRepository,
            UserRepository userRepository, PlaceConversionEventService conversionEventService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationStatusHistoryRepository = null;
        this.availabilityRepository = availabilityRepository;
        this.availabilityService = availabilityService;
        this.availabilityAccessPolicy = availabilityAccessPolicy;
        this.ownerPlaceRepository = ownerPlaceRepository;
        this.userRepository = userRepository;
        this.mapPlaceRepository = null;
        this.conversionEventService = conversionEventService;
        this.clock = clock;
        this.quoteService = null;
    }

    /**
     * 같은 사용자·멱등 키의 요청 값이 같으면 기존 예약을 반환.
     * 신규 요청은 재고를 먼저 예약하고 PENDING 예약·이력을 저장한 뒤 전환 이벤트를 발행.
     */
    @Transactional(noRollbackFor = ConfirmedReservationRejectedException.class)
    public ReservationResponse create(Long userId, ReservationCreateRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        requireTourist(user);
        Reservation existing = reservationRepository
                .findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            if (!existing.getAvailabilityId().equals(request.availabilityId())
                    || existing.getQuantity() != request.quantity()
                    || !java.util.Objects.equals(existing.getBookerName(), request.bookerName().trim())
                    || !java.util.Objects.equals(existing.getBookerPhone(), request.bookerPhone().trim())
                    || !java.util.Objects.equals(existing.getRequestNote(), normalize(request.requestNote()))
                    || !java.util.Objects.equals(existing.getConfirmationToken(), request.confirmationToken())) {
                throw new ReservationException(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return ReservationResponse.from(existing);
        }
        if (quoteService != null) quoteService.checkPriorResult(userId, request);
        if (request.confirmationToken() != null && quoteService == null) {
            throw new ReservationException(ReservationErrorCode.QUOTE_NOT_FOUND);
        }
        var confirmation = quoteService == null ? null : quoteService.verify(userId, request);
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = availabilityService.reserve(request.availabilityId(), request.quantity());
        try {
            Reservation reservation = Reservation.create(userId, request.availabilityId(), request.idempotencyKey(), request.quantity(),
                            availability.getStartsAt(), availability.getEndsAt(), request.bookerName(),
                            request.bookerPhone(), request.requestNote(), now);
            if (confirmation != null) reservation.acceptConfirmation(request.confirmationToken(), confirmation);
            Reservation saved = reservationRepository.save(reservation);
            saveHistory(saved.getId(), saved.getStatus(), userId, null, now);
            conversionEventService.publish(
                    userId,
                    availability.getPlaceId(),
                    PlaceConversionEventType.RESERVATION,
                    saved.getId(),
                    now
            );
            return ReservationResponse.from(saved);
        } catch (IllegalArgumentException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_INPUT);
        }
    }

    @Transactional(readOnly = true)
    // 예약을 조회하고 요청 사용자가 예약자 본인인지 확인.
    public ReservationResponse getMine(Long userId, Long reservationId) {
        Reservation reservation = find(reservationId);
        requireTouristOwnership(reservation, userId);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    // 관광객 본인의 예약을 생성일 역순 페이지로 조회.
    public ReservationPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        return toPageResponse(reservationRepository.findAllByTouristUserId(userId, pageRequest(page, limit)),
                page, limit);
    }

    @Transactional(readOnly = true)
    public ReservationPageResponse listOwned(Long ownerId, ReservationStatus status, Long placeId,
            LocalDateTime reservationFrom, LocalDateTime reservationTo, int page, int limit) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        validatePeriod(reservationFrom, reservationTo);
        return toPageResponse(reservationRepository.findAllOwned(ownerId, status, placeId,
                reservationFrom != null, reservationFrom, reservationTo != null, reservationTo,
                pageRequest(page, limit)), page, limit);
    }

    @Transactional(readOnly = true)
    public MerchantReservationDetailResponse getOwned(Long ownerId, Long reservationId) {
        Reservation reservation = find(reservationId);
        PlaceAvailability availability = findAvailability(reservation.getAvailabilityId());
        requireCurrentOwner(ownerId, ownerPlaceRepository.findById(availability.getPlaceId()).orElse(null));
        MapPlace place = mapPlaceRepository.findById(availability.getPlaceId()).orElse(null);
        return new MerchantReservationDetailResponse(ReservationResponse.from(reservation), availability.getPlaceId(),
                place == null ? null : place.getName(), reservationStatusHistoryRepository
                        .findAllByReservationIdOrderByChangedAtAscIdAsc(reservationId).stream()
                        .map(ReservationStatusHistoryResponse::from).toList());
    }

    /** 기존 내부 호출도 심사자와 상태 이력을 기록하는 동일한 승인 경로를 사용한다. */
    @Deprecated
    @Transactional
    public ReservationResponse confirm(Long ownerId, Long reservationId) {
        return confirmOwned(ownerId, reservationId, null);
    }

    @Transactional
    // 예약자 본인인지 확인하고 예약을 취소해 재고를 반환.
    public ReservationResponse cancelMine(Long userId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireTouristOwnership(reservation, userId);
        return cancel(reservation, userId);
    }

    @Transactional
    // 장소 소유자가 관리하는 예약인지 확인하고 예약을 취소.
    public ReservationResponse cancelOwned(Long ownerId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        return cancel(reservation, ownerId);
    }

    // 예약 상태를 취소로 전환하고 예약 수량을 가용 재고로 반환.
    private ReservationResponse cancel(Reservation reservation, Long canceledBy) {
        try {
            if (quoteService != null) quoteService.requireCancellationAllowed(reservation);
            LocalDateTime now = LocalDateTime.now(clock);
            reservation.cancel(canceledBy, now);
            availabilityService.release(reservation.getAvailabilityId(), reservation.getQuantity());
            saveHistory(reservation.getId(), reservation.getStatus(), canceledBy, null, now);
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    /**
     * 상태·장소·점주·예약자·예약 기간으로 예약을 조회하고 관련 이름과 상태 이력을 포함한 관리자 페이지를 반환.
     * 기간 양 끝이 있으면 시작이 종료보다 앞서야 하며 누락된 경계는 저장소 필터를 비활성화.
     */
    @Transactional(readOnly = true)
    public AdminReservationPageResponse listForAdmin(ReservationStatus status, Long placeId, Long ownerId,
            Long touristUserId, LocalDateTime reservationFrom, LocalDateTime reservationTo,
            int page, int limit) {
        validatePeriod(reservationFrom, reservationTo);
        Page<Reservation> reservations = reservationRepository.findAllForAdmin(status, placeId, ownerId, touristUserId,
                reservationFrom != null, reservationFrom, reservationTo != null, reservationTo,
                pageRequest(page, limit));
        return new AdminReservationPageResponse(reservations.getContent().stream().map(this::toAdminResponse).toList(),
                page, limit, reservations.getTotalElements(), reservations.getTotalPages(), reservations.hasNext());
    }

    @Transactional(readOnly = true)
    public AdminReservationResponse getForAdmin(Long reservationId) { return toAdminResponse(find(reservationId)); }

    /** 현재 장소 소유권을 잠금 확인하고 승인 결과와 이력을 같은 트랜잭션으로 저장한다. */
    @Transactional
    public ReservationResponse confirmOwned(Long ownerId, Long reservationId, String reason) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            reservation.confirm(ownerId, reason, now);
            saveHistory(reservation.getId(), reservation.getStatus(), ownerId, reservation.getReviewReason(), now);
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    /** 대기 예약만 반려하고 선점 재고 반환과 상태 이력 저장을 원자적으로 처리한다. */
    @Transactional
    public ReservationResponse rejectOwned(Long ownerId, Long reservationId, String reason) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            reservation.reject(ownerId, reason, now);
            availabilityService.release(reservation.getAvailabilityId(), reservation.getQuantity());
            saveHistory(reservation.getId(), reservation.getStatus(), ownerId, reservation.getReviewReason(), now);
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        } catch (IllegalArgumentException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_INPUT);
        }
    }

    private void saveHistory(Long reservationId, ReservationStatus status, Long changedBy, String reason,
            LocalDateTime changedAt) {
        if (reservationStatusHistoryRepository != null) {
            reservationStatusHistoryRepository.save(ReservationStatusHistory.of(
                    reservationId, status, changedBy, reason, changedAt));
        }
    }

    private AdminReservationResponse toAdminResponse(Reservation reservation) {
        PlaceAvailability availability = findAvailability(reservation.getAvailabilityId());
        User tourist = userRepository.findById(reservation.getTouristUserId()).orElse(null);
        Long currentOwnerId = ownerPlaceRepository.findById(availability.getPlaceId())
                .map(MerchantOwnerPlace::getMerchantOwnerUserId).orElse(null);
        User owner = currentOwnerId == null ? null : userRepository.findById(currentOwnerId).orElse(null);
        MapPlace place = mapPlaceRepository.findById(availability.getPlaceId()).orElse(null);
        return AdminReservationResponse.of(reservation, tourist == null ? null : tourist.getUsername(),
                currentOwnerId, owner == null ? null : owner.getUsername(), availability.getPlaceId(),
                place == null ? null : place.getName(),
                reservationStatusHistoryRepository
                        .findAllByReservationIdOrderByChangedAtAscIdAsc(reservation.getId()).stream()
                        .map(AdminReservationStatusHistoryResponse::from).toList());
    }

    // 사용자 ID로 관광객 계정을 조회해 유효성을 검증.
    private void requireTourist(Long userId) {
        requireTourist(userRepository.findById(userId).orElse(null));
    }

    // 탈퇴·정지되지 않은 일반 사용자 계정인지 확인.
    private void requireTourist(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new ReservationException(ReservationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    // 예약자 본인인지 확인.
    private void requireTouristOwnership(Reservation reservation, Long userId) {
        requireTourist(userId);
        if (!reservation.getTouristUserId().equals(userId)) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
    }

    // 예약 처리 중 소유권 이전과 경합하지 않도록 현재 장소 소유 매핑을 잠근다.
    private void requireAvailabilityOwner(Long ownerId, Long availabilityId) {
        PlaceAvailability availability = findAvailability(availabilityId);
        requireCurrentOwner(ownerId, ownerPlaceRepository.findByPlaceIdForUpdate(availability.getPlaceId()).orElse(null));
    }

    private void requireCurrentOwner(Long ownerId, MerchantOwnerPlace ownerPlace) {
        if (ownerPlace == null || !ownerPlace.getMerchantOwnerUserId().equals(ownerId)) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
    }

    private PlaceAvailability findAvailability(Long availabilityId) {
        return availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_INPUT);
        }
    }

    // 생성일과 ID 내림차순 페이지 요청을 생성.
    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    // Spring Page 결과를 API 페이지 응답 DTO로 변환.
    private ReservationPageResponse toPageResponse(Page<Reservation> reservations, int page, int limit) {
        return new ReservationPageResponse(reservations.getContent().stream().map(ReservationResponse::from).toList(),
                page, limit, reservations.getTotalElements(), reservations.getTotalPages(), reservations.hasNext());
    }

    // 예약을 조회하고 없으면 도메인 예외를 발생시킴.
    private Reservation find(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    // 상태 변경을 위해 예약을 비관적 잠금으로 조회.
    private Reservation findForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
