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

    /**
     * 관광객 계정을 확인하고 지정 장소의 체류 인증을 시작하거나 당일 기존 세션을 반환한다.
     * 새 세션에만 현재 정책을 확정하므로 설정이 바뀌어도 기존 세션의 반경·체류 시간은 유지된다.
     */
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

    /**
     * 당일 진행 세션을 최근 서버 확인 시각 순으로 검사해 재사용 가능한 첫 세션을 반환한다.
     * 재사용 검사 과정에서 오래된 세션을 만료 상태로 바꿀 수 있다.
     */
    private VisitVerificationSession findExistingForegroundSession(Long userId,
            ForegroundVisitVerificationStartRequest request, Instant now) {
        LocalDate verificationDate = LocalDate.ofInstant(now, VERIFICATION_ZONE);
        return sessionRepository.findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
                        userId, verificationDate, ACTIVE_STATUSES).stream()
                .filter(session -> isReusableForegroundSession(session, request, now))
                .findFirst()
                .orElse(null);
    }

    /**
     * TTL 또는 관측 공백을 넘긴 세션은 만료 처리한다.
     * 그 밖에는 현재 공개·운영 장소의 저장된 반경 안에 있을 때만 재사용한다.
     */
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

    /**
     * 같은 사용자·장소·서울 날짜의 완료 세션을 우선하고 이어 진행 세션을 찾는다.
     * 기존 세션 반환 경로에서는 관측 정확도·시각만 검증하며 장소 상태·거리·세션 만료는 다시 판정하지 않는다.
     * 새 세션이면 장소 및 반경을 확인하고 활동 세션 유일 제약 위반을 별도 중복 오류로 변환한다.
     */
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

    /**
     * 소유자 조건과 쓰기 잠금으로 세션을 조회해 동시 관측 처리를 직렬화한다.
     * 종료 상태는 그대로 반환하고 TTL·공백 초과는 만료, 반경 이탈은 이탈 상태로 기록한다.
     * 충분한 서버 경과 시간이 쌓이면 같은 트랜잭션에서 체류 체크인을 저장하고 세션을 완료한다.
     */
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
        // 클라이언트 관측 시각은 순서·유효성 검증에 사용하고 체류 누적은 서버 시각을 기준으로 한다.
        session.recordObservation(request.observedAt(), now, distanceMeters);
        if (session.getVerifiedDwellSeconds() < session.getRequiredDwellSeconds()) {
            return VisitVerificationSessionResponse.from(session, properties);
        }
        LocationCheckIn completedCheckIn = completeCheckIn(session, request.observedAt(), now, distanceMeters);
        session.complete(now, completedCheckIn.getId());
        return VisitVerificationSessionResponse.from(session, properties);
    }

    /**
     * 본인 세션 조회 시에도 쓰기 잠금을 잡고 만료 조건이면 상태를 갱신한다.
     * 조회 API지만 만료 처리가 있으므로 readOnly 트랜잭션이 아니다.
     */
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

    /**
     * 완료 시점의 서울 날짜로 DWELL_VERIFIED 체크인을 저장한다.
     * 당일 체류 인증 유일 제약 위반만 전용 중복 오류로 변환하며 다른 DB 실패는 그대로 전달한다.
     */
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

    /** 운영 중이며 공개된 장소만 허용하고, 미존재·비공개·비운영을 같은 장소 부재 오류로 처리한다. */
    private MapPlace requireAvailablePlace(Long placeId) {
        MapPlace place = placeRepository.findById(placeId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND));
        if (place.getOperatingStatus() != PlaceOperatingStatus.OPERATING
                || place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND);
        }
        return place;
    }

    /**
     * 정확도는 전역 상한과 세션 반경 중 작은 값 이하만 허용한다.
     * 관측 시각은 과거 TTL부터 미래 허용 오차까지 경계를 포함해 인정한다.
     */
    private void validateObservation(double accuracyMeters, Instant observedAt, Instant now, double radiusMeters) {
        if (accuracyMeters > Math.min(properties.maxAccuracyMeters(), radiusMeters)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
        }
        if (observedAt.isBefore(now.minus(properties.observationTtl()))
                || observedAt.isAfter(now.plus(properties.futureTolerance()))) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
        }
    }

    /** USER 역할의 미탈퇴·현재 미정지 계정만 체류 인증에 참여할 수 있다. */
    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    /** 원인 예외 체인에서 제약 이름을 찾아 알려진 중복 오류만 변환하도록 구분한다. */
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
