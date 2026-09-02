package com.typenull.pingdom.reservation.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.AvailabilityProductType;
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
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
/** 예약 생성·조회·확정·취소를 가용성, 상품, 결제 정책과 연결합니다. */
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final PlaceAvailabilityRepository availabilityRepository;
    private final PlaceAvailabilityService availabilityService;
    private final AvailabilityAccessPolicy availabilityAccessPolicy;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final ReservableProductRepository reservableProductRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final PlaceConversionEventService conversionEventService;
    private final Clock clock;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
            ReservationStatusHistoryRepository reservationStatusHistoryRepository,
            PlaceAvailabilityRepository availabilityRepository, PlaceAvailabilityService availabilityService,
            AvailabilityAccessPolicy availabilityAccessPolicy, MerchantOwnerPlaceRepository ownerPlaceRepository,
            UserRepository userRepository, MapPlaceRepository mapPlaceRepository,
            ReservableProductRepository reservableProductRepository, AdminAuditLogService adminAuditLogService,
            PlaceConversionEventService conversionEventService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationStatusHistoryRepository = reservationStatusHistoryRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityService = availabilityService;
        this.availabilityAccessPolicy = availabilityAccessPolicy;
        this.ownerPlaceRepository = ownerPlaceRepository;
        this.userRepository = userRepository;
        this.mapPlaceRepository = mapPlaceRepository;
        this.reservableProductRepository = reservableProductRepository;
        this.adminAuditLogService = adminAuditLogService;
        this.conversionEventService = conversionEventService;
        this.clock = clock;
    }

    /** 기존 단위 테스트와 내부 호출의 생성자 호환을 위한 보조 생성자입니다. */
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
        this.reservableProductRepository = null;
        this.adminAuditLogService = null;
        this.conversionEventService = conversionEventService;
        this.clock = clock;
    }

    @Transactional
    // 관광객과 재고를 검증하고 멱등 예약을 생성한 뒤 전환 이벤트를 발행합니다.
    public ReservationResponse create(Long userId, ReservationCreateRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        requireTourist(user);
        Reservation existing = reservationRepository
                .findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            if (!existing.getAvailabilityId().equals(request.availabilityId())
                    || existing.getQuantity() != request.quantity()) {
                throw new ReservationException(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return ReservationResponse.from(existing);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = availabilityService.reserve(request.availabilityId(), request.quantity());
        try {
            Reservation saved = reservationRepository.save(
                    Reservation.create(userId, request.availabilityId(), availability.getProductId(),
                            availability.getProductType(), request.idempotencyKey(), request.quantity(), now));
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
    // 예약을 조회하고 요청 사용자가 예약자 본인인지 확인합니다.
    public ReservationResponse getMine(Long userId, Long reservationId) {
        Reservation reservation = find(reservationId);
        requireTouristOwnership(reservation, userId);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    // 관광객 본인의 예약을 생성일 역순 페이지로 조회합니다.
    public ReservationPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        return toPageResponse(reservationRepository.findAllByTouristUserId(userId, pageRequest(page, limit)),
                page, limit);
    }

    @Transactional(readOnly = true)
    // 활성 사업자 소유자가 관리하는 장소의 예약을 페이지로 조회합니다.
    public ReservationPageResponse listOwned(Long ownerId, int page, int limit) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return toPageResponse(reservationRepository.findAllOwned(ownerId, pageRequest(page, limit)), page, limit);
    }

    /** HTTP 엔드포인트는 제거됐으며 기존 내부 호출 호환을 위해서만 유지합니다. */
    @Deprecated
    @Transactional
    public ReservationResponse confirm(Long ownerId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        try {
            reservation.confirm(LocalDateTime.now(clock));
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    @Transactional
    // 예약자 본인인지 확인하고 예약을 취소해 재고를 반환합니다.
    public ReservationResponse cancelMine(Long userId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireTouristOwnership(reservation, userId);
        return cancel(reservation, userId);
    }

    @Transactional
    // 장소 소유자가 관리하는 예약인지 확인하고 예약을 취소합니다.
    public ReservationResponse cancelOwned(Long ownerId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        return cancel(reservation, ownerId);
    }

    // 예약 상태를 취소로 전환하고 예약 수량을 가용 재고로 반환합니다.
    private ReservationResponse cancel(Reservation reservation, Long canceledBy) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            reservation.cancel(canceledBy, now);
            availabilityService.release(reservation.getAvailabilityId(), reservation.getQuantity());
            saveHistory(reservation.getId(), reservation.getStatus(), canceledBy, null, now);
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    @Transactional(readOnly = true)
    public AdminReservationPageResponse listForAdmin(ReservationStatus status, Long placeId, Long ownerId,
            Long touristUserId, Long productId, LocalDateTime reservationFrom, LocalDateTime reservationTo,
            int page, int limit) {
        if (reservationFrom != null && reservationTo != null && !reservationFrom.isBefore(reservationTo)) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_INPUT);
        }
        Page<Reservation> reservations = reservationRepository.findAllForAdmin(status, placeId, ownerId, touristUserId,
                productId, reservationFrom != null, reservationFrom, reservationTo != null, reservationTo,
                pageRequest(page, limit));
        return new AdminReservationPageResponse(reservations.getContent().stream().map(this::toAdminResponse).toList(),
                page, limit, reservations.getTotalElements(), reservations.getTotalPages(), reservations.hasNext());
    }

    @Transactional(readOnly = true)
    public AdminReservationResponse getForAdmin(Long reservationId) { return toAdminResponse(find(reservationId)); }

    @Transactional
    public AdminReservationResponse confirmByAdmin(Long adminUserId, Long reservationId, String reason) {
        Reservation reservation = findForUpdate(reservationId);
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            ReservationStatus before = reservation.getStatus();
            reservation.confirm(adminUserId, reason, now);
            recordReview(adminUserId, reservation, before, reason, now, AdminAuditAction.RESERVATION_CONFIRMED);
            return toAdminResponse(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    @Transactional
    public AdminReservationResponse rejectByAdmin(Long adminUserId, Long reservationId, String reason) {
        Reservation reservation = findForUpdate(reservationId);
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            ReservationStatus before = reservation.getStatus();
            reservation.reject(adminUserId, reason, now);
            availabilityService.release(reservation.getAvailabilityId(), reservation.getQuantity());
            recordReview(adminUserId, reservation, before, reason, now, AdminAuditAction.RESERVATION_REJECTED);
            return toAdminResponse(reservation);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    private void recordReview(Long adminUserId, Reservation reservation, ReservationStatus before, String reason,
            LocalDateTime now, AdminAuditAction action) {
        saveHistory(reservation.getId(), reservation.getStatus(), adminUserId, reason, now);
        adminAuditLogService.record(adminUserId, action, AdminAuditTargetType.RESERVATION, reservation.getId(), reason,
                java.util.Map.of("status", before), java.util.Map.of("status", reservation.getStatus()));
    }

    private void saveHistory(Long reservationId, ReservationStatus status, Long changedBy, String reason,
            LocalDateTime changedAt) {
        if (reservationStatusHistoryRepository != null) {
            reservationStatusHistoryRepository.save(ReservationStatusHistory.of(
                    reservationId, status, changedBy, reason, changedAt));
        }
    }

    private AdminReservationResponse toAdminResponse(Reservation reservation) {
        PlaceAvailability availability = availabilityRepository.findById(reservation.getAvailabilityId())
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        User tourist = userRepository.findById(reservation.getTouristUserId()).orElse(null);
        User owner = userRepository.findById(availability.getMerchantOwnerUserId()).orElse(null);
        MapPlace place = mapPlaceRepository.findById(availability.getPlaceId()).orElse(null);
        ReservableProduct product = reservation.getProductId() == null ? null
                : reservableProductRepository.findById(reservation.getProductId()).orElse(null);
        return AdminReservationResponse.of(reservation, tourist == null ? null : tourist.getUsername(),
                availability.getMerchantOwnerUserId(), owner == null ? null : owner.getUsername(), availability.getPlaceId(),
                place == null ? null : place.getName(), availability.getStartsAt(), availability.getEndsAt(),
                product == null ? null : product.getName(), reservationStatusHistoryRepository
                        .findAllByReservationIdOrderByChangedAtAscIdAsc(reservation.getId()).stream()
                        .map(AdminReservationStatusHistoryResponse::from).toList());
    }

    // 사용자 ID로 관광객 계정을 조회해 유효성을 검증합니다.
    private void requireTourist(Long userId) {
        requireTourist(userRepository.findById(userId).orElse(null));
    }

    // 탈퇴·정지되지 않은 일반 사용자 계정인지 확인합니다.
    private void requireTourist(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new ReservationException(ReservationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    // 예약자 본인인지 확인합니다.
    private void requireTouristOwnership(Reservation reservation, Long userId) {
        requireTourist(userId);
        if (!reservation.getTouristUserId().equals(userId)) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
    }

    // 가용 상품의 장소 소유자이며 활성 사업자인지 검증합니다.
    private void requireAvailabilityOwner(Long ownerId, Long availabilityId) {
        PlaceAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        MerchantOwnerPlace ownerPlace = ownerPlaceRepository.findByPlaceIdForUpdate(availability.getPlaceId())
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN));
        if (!ownerPlace.getMerchantOwnerUserId().equals(ownerId)) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
    }

    // 생성일과 ID 내림차순 페이지 요청을 생성합니다.
    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    // Spring Page 결과를 API 페이지 응답 DTO로 변환합니다.
    private ReservationPageResponse toPageResponse(Page<Reservation> reservations, int page, int limit) {
        return new ReservationPageResponse(reservations.getContent().stream().map(ReservationResponse::from).toList(),
                page, limit, reservations.getTotalElements(), reservations.getTotalPages(), reservations.hasNext());
    }

    // 예약을 조회하고 없으면 도메인 예외를 발생시킵니다.
    private Reservation find(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    // 상태 변경을 위해 예약을 비관적 잠금으로 조회합니다.
    private Reservation findForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }
}
