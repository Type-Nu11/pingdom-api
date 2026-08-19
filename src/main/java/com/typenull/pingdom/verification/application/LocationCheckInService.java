package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/** 위치·시간·중복 조건을 검증해 방문 인증용 check-in 상태를 생성하고 조회합니다. */
public class LocationCheckInService {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final ZoneId CHECK_IN_ZONE = ZoneId.of("Asia/Seoul");

    private final LocationCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;
    private final LocationCheckInProperties properties;

    @Transactional
    /** 요청 좌표가 장소 반경과 허용 시간 조건을 만족하는지 검증한 뒤 check-in을 저장합니다. */
    public LocationCheckInResponse checkIn(Long userId, LocationCheckInRequest request) {
        requireTourist(userId);
        Instant now = clock.instant();
        validateObservation(request, now);
        MapPlace place = placeRepository.findById(request.placeId())
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND));
        if (place.getOperatingStatus() != PlaceOperatingStatus.OPERATING
                || place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND);
        }
        double distance = distanceMeters(request.latitude(), request.longitude(), place.getLatitude(),
                place.getLongitude());
        if (distance > properties.maxDistanceMeters()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.OUTSIDE_CHECK_IN_RADIUS);
        }
        LocalDate checkInDate = LocalDate.ofInstant(now, CHECK_IN_ZONE);
        if (checkInRepository.existsByTouristUserIdAndPlaceIdAndCheckInDate(userId, request.placeId(), checkInDate)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
        }
        try {
            LocationCheckIn saved = checkInRepository.saveAndFlush(LocationCheckIn.proximityMatched(userId,
                    request.placeId(), checkInDate, request.observedAt(), now, distance));
            return LocationCheckInResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_location_check_in_daily")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LocationCheckInPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        Page<LocationCheckIn> checkIns = checkInRepository.findAllByTouristUserId(userId,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
        return new LocationCheckInPageResponse(checkIns.getContent().stream().map(LocationCheckInResponse::from).toList(),
                page, limit, checkIns.getTotalElements(), checkIns.getTotalPages(), checkIns.hasNext());
    }

    private void validateObservation(LocationCheckInRequest request, Instant now) {
        if (request.accuracyMeters() > properties.maxAccuracyMeters()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
        }
        if (request.observedAt().isBefore(now.minus(properties.observationTtl()))
                || request.observedAt().isAfter(now.plus(properties.futureTolerance()))) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
        }
    }

    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    static double distanceMeters(double latitude, double longitude, double placeLatitude, double placeLongitude) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(placeLatitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(placeLongitude - longitude);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) return true;
            current = current.getCause();
        }
        return false;
    }
}
