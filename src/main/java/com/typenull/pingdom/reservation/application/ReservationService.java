package com.typenull.pingdom.reservation.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final PlaceAvailabilityRepository availabilityRepository;
    private final PlaceAvailabilityService availabilityService;
    private final AvailabilityAccessPolicy availabilityAccessPolicy;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
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
        availabilityService.reserve(request.availabilityId(), request.quantity());
        try {
            return ReservationResponse.from(reservationRepository.save(
                    Reservation.create(userId, request.availabilityId(), request.idempotencyKey(),
                            request.quantity(), now)));
        } catch (IllegalArgumentException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public ReservationResponse getMine(Long userId, Long reservationId) {
        Reservation reservation = find(reservationId);
        requireTouristOwnership(reservation, userId);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        return toPageResponse(reservationRepository.findAllByTouristUserId(userId, pageRequest(page, limit)),
                page, limit);
    }

    @Transactional(readOnly = true)
    public ReservationPageResponse listOwned(Long ownerId, int page, int limit) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return toPageResponse(reservationRepository.findAllOwned(ownerId, pageRequest(page, limit)), page, limit);
    }

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
    public ReservationResponse cancelMine(Long userId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireTouristOwnership(reservation, userId);
        return cancel(reservation);
    }

    @Transactional
    public ReservationResponse cancelOwned(Long ownerId, Long reservationId) {
        Reservation reservation = findForUpdate(reservationId);
        requireAvailabilityOwner(ownerId, reservation.getAvailabilityId());
        return cancel(reservation);
    }

    private ReservationResponse cancel(Reservation reservation) {
        try {
            reservation.cancel(LocalDateTime.now(clock));
            availabilityService.release(reservation.getAvailabilityId(), reservation.getQuantity());
            return ReservationResponse.from(reservation);
        } catch (IllegalStateException exception) {
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    private void requireTourist(Long userId) {
        requireTourist(userRepository.findById(userId).orElse(null));
    }

    private void requireTourist(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new ReservationException(ReservationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    private void requireTouristOwnership(Reservation reservation, Long userId) {
        requireTourist(userId);
        if (!reservation.getTouristUserId().equals(userId)) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }
    }

    private void requireAvailabilityOwner(Long ownerId, Long availabilityId) {
        PlaceAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        availabilityAccessPolicy.requireOwnedPlace(ownerId, availability.getPlaceId(), LocalDateTime.now(clock));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private ReservationPageResponse toPageResponse(Page<Reservation> reservations, int page, int limit) {
        return new ReservationPageResponse(reservations.getContent().stream().map(ReservationResponse::from).toList(),
                page, limit, reservations.getTotalElements(), reservations.getTotalPages(), reservations.hasNext());
    }

    private Reservation find(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation findForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }
}
