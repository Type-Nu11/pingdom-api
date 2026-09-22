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

/**
 * 일반 관광객 계정의 관측 정확도·시각·장소 공개 상태·거리를 확인해 근접 체크인을 생성한다.
 * 서울 날짜 기준 사용자·장소별 하루 한 건으로 제한하며 DB 유일 제약으로 경합 시 중복도 처리한다.
 */
@Service
@RequiredArgsConstructor
public class LocationCheckInService {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final ZoneId CHECK_IN_ZONE = ZoneId.of("Asia/Seoul");

    private final LocationCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;
    private final LocationCheckInProperties properties;

    /**
     * 접근 가능한 장소의 허용 반경 안에 있는 관측으로 PROXIMITY_MATCHED 체크인을 저장한다.
     * 요청 관측 날짜가 아닌 현재 서버 시각의 서울 날짜로 일일 중복을 판정한다.
     * 알려진 일일 유일 제약 위반만 도메인 중복 오류로 변환하며 다른 DB 오류는 전파한다.
     */
    @Transactional
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
        // 사전 조회 뒤 동시 요청이 들어올 수 있으므로 flush 시점의 유일 제약도 확인한다.
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

    /**
     * 관광객 본인의 체크인을 기록 시각·ID 역순으로 조회한다.
     * 외부 페이지 번호는 1부터 시작하며 내부 PageRequest에는 1을 뺀 값을 전달한다.
     */
    @Transactional(readOnly = true)
    public LocationCheckInPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        Page<LocationCheckIn> checkIns = checkInRepository.findAllByTouristUserId(userId,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
        return new LocationCheckInPageResponse(checkIns.getContent().stream().map(LocationCheckInResponse::from).toList(),
                page, limit, checkIns.getTotalElements(), checkIns.getTotalPages(), checkIns.hasNext());
    }

    /** 정확도 상한과 현재 시각 기준 과거 TTL·미래 허용 오차를 확인한다. 경계 시각과 정확도 상한은 허용한다. */
    private void validateObservation(LocationCheckInRequest request, Instant now) {
        if (request.accuracyMeters() > properties.maxAccuracyMeters()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
        }
        if (request.observedAt().isBefore(now.minus(properties.observationTtl()))
                || request.observedAt().isAfter(now.plus(properties.futureTolerance()))) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
        }
    }

    /** 존재하는 USER 역할 중 탈퇴·현재 정지 계정을 제외한다. 모두 같은 관광객 계정 필요 오류로 거부한다. */
    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    /** 위·경도(도)를 라디안으로 변환해 구면 Haversine 거리(미터)를 계산한다. 도로 경로 거리는 아니다. */
    static double distanceMeters(double latitude, double longitude, double placeLatitude, double placeLongitude) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(placeLatitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(placeLongitude - longitude);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 원인 체인에 지정한 Hibernate 제약 이름이 있는지 확인해 중복 오류 변환 대상을 좁힌다. */
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
