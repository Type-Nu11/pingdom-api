package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서버 수신 시각과 연속 관측 간격으로 장소 체류 시간을 판정합니다. */
@Service
@RequiredArgsConstructor
public class VisitVerificationService {
    private static final ZoneId VERIFICATION_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<VisitVerificationSessionStatus> ACTIVE_STATUSES = List.of(
            VisitVerificationSessionStatus.STARTED, VisitVerificationSessionStatus.IN_PROGRESS);

    private final VisitVerificationSessionRepository sessionRepository;
    private final LocationCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;
    private final VisitVerificationProperties properties;
    private final VisitVerificationPolicyResolver policyResolver;

    @Transactional
    public VisitVerificationSessionResponse start(Long userId, VisitVerificationStartRequest request) {
        requireTourist(userId);
        return start(userId, request, null);
    }

    /** 장소 ID를 신뢰하지 않고 좌표 주변의 단일 공개 장소를 서버가 선택합니다. */
    @Transactional
    public VisitVerificationSessionResponse startForeground(Long userId,
            ForegroundVisitVerificationStartRequest request) {
        requireTourist(userId);
        Instant now = clock.instant();
        validateObservation(request.accuracyMeters(), request.observedAt(), now, properties.foregroundRadiusMeters());

        // 진행 중인 동일 장소 세션은 새 후보 탐색보다 먼저 복구합니다.
        VisitVerificationSession existing = findExistingForegroundSession(userId, request, now);
        if (existing != null) {
            validateObservation(request.accuracyMeters(), request.observedAt(), now, existing.getRequiredRadiusMeters());
            return VisitVerificationSessionResponse.from(existing, properties);
        }

        List<MapPlaceRepository.NearbyVisitPlace> candidates = placeRepository.findNearbyPlacesForVisitVerification(
                request.latitude(), request.longitude(), properties.foregroundRadiusMeters(), PageRequest.of(0, 2));
        if (candidates.isEmpty()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_NOT_FOUND);
        }

        Long placeId = selectForegroundPlace(candidates, request.accuracyMeters());
        return start(userId, new VisitVerificationStartRequest(placeId, request.latitude(), request.longitude(),
                request.accuracyMeters(), request.observedAt()), new VisitVerificationPolicy(
                        properties.foregroundRadiusMeters(), properties.foregroundDwellDuration()));
    }

    /**
     * 후보 간 거리 차이가 GPS 정확도 두 배보다 작거나 같으면 실제로 어느 장소가 더 가까운지 보장할 수 없습니다.
     * Repository 정렬(distance, placeId)로 재현성은 확보하되, 불확실한 동률을 임의 선택하지 않습니다.
     */
    private Long selectForegroundPlace(List<MapPlaceRepository.NearbyVisitPlace> candidates, double accuracyMeters) {
        MapPlaceRepository.NearbyVisitPlace nearest = candidates.getFirst();
        if (candidates.size() == 1) return nearest.getPlaceId();

        double distanceGap = candidates.get(1).getDistanceMeters() - nearest.getDistanceMeters();
        if (distanceGap <= accuracyMeters * 2) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_AMBIGUOUS);
        }
        return nearest.getPlaceId();
    }

    private VisitVerificationSession findExistingForegroundSession(Long userId,
            ForegroundVisitVerificationStartRequest request, Instant now) {
        LocalDate verificationDate = LocalDate.ofInstant(now, VERIFICATION_ZONE);
        return sessionRepository.findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
                        userId, verificationDate, ACTIVE_STATUSES).stream()
                .filter(session -> isReusableForegroundSession(session, request, now))
                .findFirst()
                .orElse(null);
    }

    private boolean isReusableForegroundSession(VisitVerificationSession session,
            ForegroundVisitVerificationStartRequest request, Instant now) {
        if (session.isExpiredAt(now) || session.hasObservationGapExceeded(now, properties.maxObservationGap())) {
            session.expire(now);
            return false;
        }
        MapPlace place = placeRepository.findById(session.getPlaceId()).orElse(null);
        if (place == null || place.getOperatingStatus() != PlaceOperatingStatus.OPERATING
                || place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE) {
            return false;
        }
        double distanceMeters = LocationCheckInService.distanceMeters(request.latitude(), request.longitude(),
                place.getLatitude(), place.getLongitude());
        return distanceMeters <= session.getRequiredRadiusMeters();
    }

    private VisitVerificationSessionResponse start(Long userId, VisitVerificationStartRequest request,
            VisitVerificationPolicy requestedPolicy) {
        requireTourist(userId);
        Instant now = clock.instant();
        LocalDate verificationDate = LocalDate.ofInstant(now, VERIFICATION_ZONE);

        VisitVerificationSession existing = sessionRepository
                .findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
                        userId, request.placeId(), verificationDate, List.of(VisitVerificationSessionStatus.COMPLETED))
                .orElseGet(() -> sessionRepository
                        .findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
                                userId, request.placeId(), verificationDate, ACTIVE_STATUSES)
                        .orElse(null));
        if (existing != null) {
            validateObservation(request.accuracyMeters(), request.observedAt(), now, existing.getRequiredRadiusMeters());
            return VisitVerificationSessionResponse.from(existing, properties);
        }

        MapPlace place = requireAvailablePlace(request.placeId());
        VisitVerificationPolicy policy = requestedPolicy == null
                ? policyResolver.resolve(place.getId())
                : requestedPolicy;
        validateObservation(request.accuracyMeters(), request.observedAt(), now, policy.requiredRadiusMeters());
        double distanceMeters = LocationCheckInService.distanceMeters(request.latitude(), request.longitude(),
                place.getLatitude(), place.getLongitude());
        if (distanceMeters > policy.requiredRadiusMeters()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.OUTSIDE_CHECK_IN_RADIUS);
        }
        try {
            VisitVerificationSession session = sessionRepository.saveAndFlush(VisitVerificationSession.start(userId,
                    place.getId(), verificationDate, request.observedAt(), now, distanceMeters,
                    policy.requiredRadiusMeters(), policy.requiredDwellDuration(), properties.sessionTtl()));
            return VisitVerificationSessionResponse.from(session, properties);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visit_verification_session_active")) {
                throw new VisitorVerificationException(
                        VisitorVerificationErrorCode.ACTIVE_VISIT_VERIFICATION_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional
    public VisitVerificationSessionResponse submitObservation(Long userId, Long sessionId,
            VisitVerificationObservationRequest request) {
        requireTourist(userId);
        Instant now = clock.instant();
        VisitVerificationSession session = sessionRepository.findByIdAndTouristUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_VERIFICATION_SESSION_NOT_FOUND));
        if (!session.isActive()) return VisitVerificationSessionResponse.from(session, properties);
        if (session.isExpiredAt(now) || session.hasObservationGapExceeded(now, properties.maxObservationGap())) {
            session.expire(now);
            return VisitVerificationSessionResponse.from(session, properties);
        }
        if (request.observedAt().isBefore(session.getLastObservedAt())) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_OBSERVATION_OUT_OF_ORDER);
        }
        validateObservation(request.accuracyMeters(), request.observedAt(), now, session.getRequiredRadiusMeters());
        MapPlace place = requireAvailablePlace(session.getPlaceId());
        double distanceMeters = LocationCheckInService.distanceMeters(request.latitude(), request.longitude(),
                place.getLatitude(), place.getLongitude());
        if (distanceMeters > session.getRequiredRadiusMeters()) {
            session.loseProximity(request.observedAt(), now, distanceMeters);
            return VisitVerificationSessionResponse.from(session, properties);
        }
        session.recordObservation(request.observedAt(), now, distanceMeters);
        if (session.getVerifiedDwellSeconds() < session.getRequiredDwellSeconds()) {
            return VisitVerificationSessionResponse.from(session, properties);
        }
        LocationCheckIn completedCheckIn = completeCheckIn(session, request.observedAt(), now, distanceMeters);
        session.complete(now, completedCheckIn.getId());
        return VisitVerificationSessionResponse.from(session, properties);
    }

    @Transactional
    public VisitVerificationSessionResponse get(Long userId, Long sessionId) {
        requireTourist(userId);
        Instant now = clock.instant();
        VisitVerificationSession session = sessionRepository.findByIdAndTouristUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_VERIFICATION_SESSION_NOT_FOUND));
        if (session.isActive() && (session.isExpiredAt(now) || session.hasObservationGapExceeded(now, properties.maxObservationGap()))) {
            session.expire(now);
        }
        return VisitVerificationSessionResponse.from(session, properties);
    }

    private LocationCheckIn completeCheckIn(VisitVerificationSession session, Instant observedAt, Instant now,
            double distanceMeters) {
        LocalDate completedDate = LocalDate.ofInstant(now, VERIFICATION_ZONE);
        try {
            return checkInRepository.saveAndFlush(LocationCheckIn.dwellVerified(session.getTouristUserId(),
                    session.getPlaceId(), completedDate, observedAt, now, distanceMeters));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_location_check_in_dwell_daily")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.DAILY_VISIT_VERIFICATION_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    private MapPlace requireAvailablePlace(Long placeId) {
        MapPlace place = placeRepository.findById(placeId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND));
        if (place.getOperatingStatus() != PlaceOperatingStatus.OPERATING
                || place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND);
        }
        return place;
    }

    private void validateObservation(double accuracyMeters, Instant observedAt, Instant now, double radiusMeters) {
        if (accuracyMeters > Math.min(properties.maxAccuracyMeters(), radiusMeters)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
        }
        if (observedAt.isBefore(now.minus(properties.observationTtl()))
                || observedAt.isAfter(now.plus(properties.futureTolerance()))) {
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
