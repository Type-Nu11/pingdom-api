package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 체류 인증의 서버 시각 기반 완료·이탈·관측 공백과 세션별 정책 확정을 검증. */
class VisitVerificationServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-08-26T06:00:00Z");
    private final VisitVerificationSessionRepository sessionRepository = mock(VisitVerificationSessionRepository.class);
    private final LocationCheckInRepository checkInRepository = mock(LocationCheckInRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final MutableClock clock = new MutableClock(STARTED_AT);
    private VisitVerificationService service;

    /**
     * 기본 반경 500m·체류 30초·최대 관측 공백 15초와 활성 관광객/장소를 구성.
     * 저장 mock이 세션 ID 10, 체크인 ID 20을 부여해 DB 없이 반환 관계를 추적.
     */
    @BeforeEach
    void setUp() {
        VisitVerificationProperties properties = new VisitVerificationProperties(500.0, Map.of(), 20.0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofDays(30), 1000.0, Duration.ofSeconds(30));
        service = new VisitVerificationService(sessionRepository, checkInRepository, userRepository, placeRepository,
                clock, properties, new VisitVerificationPolicyResolver(properties));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        when(placeRepository.findById(2L)).thenReturn(Optional.of(MapPlace.builder()
                .id(2L).name("테스트 장소").address("주소").latitude(35.1801).longitude(128.1078)
                .registrant("등록자").build()));
        when(sessionRepository.findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
                eq(1L), eq(2L), any(LocalDate.class), anyCollection())).thenReturn(Optional.empty());
        when(sessionRepository.saveAndFlush(any(VisitVerificationSession.class))).thenAnswer(invocation -> {
            VisitVerificationSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 10L);
            return session;
        });
        when(checkInRepository.saveAndFlush(any(LocationCheckIn.class))).thenAnswer(invocation -> {
            LocationCheckIn checkIn = invocation.getArgument(0);
            ReflectionTestUtils.setField(checkIn, "id", 20L);
            return checkIn;
        });
    }

    /**
     * 서버 시계를 15초씩 두 번 이동하며 관측하면 STARTED → IN_PROGRESS → COMPLETED가 되어야 함.
     * 반경 500m·요구 30초, 누적 30초, 체크인 ID 20과 리뷰 가능 여부를 확인하고
     * DWELL_VERIFIED 체크인 저장이 호출되는지 검증.
     */
    @Test
    void completeContinuousDwell() {
        VisitVerificationSessionResponse started = service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findByIdAndTouristUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(session));

        clock.advance(Duration.ofSeconds(15));
        VisitVerificationSessionResponse inProgress = service.submitObservation(1L, 10L, observationRequest());
        clock.advance(Duration.ofSeconds(15));
        VisitVerificationSessionResponse completed = service.submitObservation(1L, 10L, observationRequest());

        assertThat(started.status()).isEqualTo(VisitVerificationSessionStatus.STARTED);
        assertThat(started.requiredRadiusMeters()).isEqualTo(500.0);
        assertThat(started.requiredDwellSeconds()).isEqualTo(30);
        assertThat(inProgress.status()).isEqualTo(VisitVerificationSessionStatus.IN_PROGRESS);
        assertThat(completed.status()).isEqualTo(VisitVerificationSessionStatus.COMPLETED);
        assertThat(completed.verifiedDwellSeconds()).isEqualTo(30);
        assertThat(completed.completedCheckInId()).isEqualTo(20L);
        assertThat(completed.reviewEligible()).isTrue();
        verify(checkInRepository).saveAndFlush(argThat(checkIn ->
                checkIn.getStatus() == LocationCheckInStatus.DWELL_VERIFIED));
    }

    /** 시작 5초 뒤 반경 밖 좌표 제출 시 PROXIMITY_LOST 종료와 체크인 미저장 확인. */
    @Test
    void loseProximityOutsideRadius() {
        service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findByIdAndTouristUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(5));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L,
                new VisitVerificationObservationRequest(35.1851, 128.1078, 10.0, clock.instant()));

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.PROXIMITY_LOST);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    /**
     * 현재 기본 반경 500m와 다른 20m 정책이 저장된 기존 세션을 반환하도록 구성.
     * 기존 ID와 반경·체류 시간이 유지되고 새 세션 저장이 없어야 함.
     */
    @Test
    void retainExistingSessionPolicy() {
        VisitVerificationSession existing = VisitVerificationSession.start(1L, 2L,
                LocalDate.ofInstant(STARTED_AT, ZoneId.of("Asia/Seoul")), STARTED_AT, STARTED_AT, 10.0,
                20.0, Duration.ofSeconds(30), Duration.ofMinutes(5));
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(sessionRepository.findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
                eq(1L), eq(2L), any(LocalDate.class), anyCollection())).thenReturn(Optional.of(existing));

        VisitVerificationSessionResponse response = service.start(1L, startRequest());

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.requiredRadiusMeters()).isEqualTo(20.0);
        assertThat(response.requiredDwellSeconds()).isEqualTo(30);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    /** 최대 관측 공백 15초를 넘긴 16초 뒤 관측 제출 시 EXPIRED 전이와 체크인 미저장 확인. */
    @Test
    void expireAfterObservationGap() {
        service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findByIdAndTouristUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(16));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L, observationRequest());

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    /**
     * 주변 후보가 장소 2 하나이면 해당 장소로 foreground 세션을 시작.
     * foreground 정책 반경 1,000m와 체류 30초가 적용되어야 함.
     */
    @Test
    void startSingleNearbyPlace() {
        MapPlaceRepository.NearbyVisitPlace candidate = nearbyPlace(2L, 10.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(candidate));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(response.placeId()).isEqualTo(2L);
        assertThat(response.requiredRadiusMeters()).isEqualTo(1000.0);
        assertThat(response.requiredDwellSeconds()).isEqualTo(30);
    }

    /** 주변 장소 조회가 비어 있으면 FOREGROUND_VISIT_PLACE_NOT_FOUND로 시작을 거부. */
    @Test
    void rejectMissingNearbyPlace() {
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant())))
                .isInstanceOfSatisfying(VisitorVerificationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_NOT_FOUND));
    }

    /**
     * 두 후보 거리가 10m·40m이고 GPS 정확도가 10m이면 거리 차이 30m가 오차 두 배보다 큼.
     * 더 가까운 장소 2를 선택하는지 확인.
     */
    @Test
    void selectDistinctNearestPlace() {
        MapPlaceRepository.NearbyVisitPlace first = nearbyPlace(2L, 10.0);
        MapPlaceRepository.NearbyVisitPlace second = nearbyPlace(3L, 40.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(first, second));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(response.placeId()).isEqualTo(2L);
    }

    /**
     * 10m·20m 거리 후보에 정확도 10m를 전달하면 오차를 고려해 가까운 장소를 구분할 수 없음.
     * 임의 선택 대신 FOREGROUND_VISIT_PLACE_AMBIGUOUS 오류로 거부.
     */
    @Test
    void rejectAmbiguousNearbyPlaces() {
        MapPlaceRepository.NearbyVisitPlace first = nearbyPlace(2L, 10.0);
        MapPlaceRepository.NearbyVisitPlace second = nearbyPlace(3L, 20.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant())))
                .isInstanceOfSatisfying(VisitorVerificationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_AMBIGUOUS));
    }

    /** 두 후보 거리가 모두 10m이면 입력 목록 순서로 장소를 선택하지 않고 모호한 장소 오류로 거부. */
    @Test
    void rejectEquidistantPlaces() {
        MapPlaceRepository.NearbyVisitPlace first = nearbyPlace(2L, 10.0);
        MapPlaceRepository.NearbyVisitPlace second = nearbyPlace(3L, 10.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant())))
                .isInstanceOfSatisfying(VisitorVerificationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_AMBIGUOUS));
    }

    /**
     * 현재 반경 안의 진행 세션 ID 11이 있으면 해당 세션과 장소 2를 반환.
     * 새 주변 장소 조회 및 새 세션 저장이 모두 호출되지 않아야 함.
     */
    @Test
    void reuseNearbyActiveSession() {
        VisitVerificationSession existing = VisitVerificationSession.start(1L, 2L,
                LocalDate.ofInstant(STARTED_AT, ZoneId.of("Asia/Seoul")), STARTED_AT, STARTED_AT, 15.0,
                1000.0, Duration.ofSeconds(30), Duration.ofMinutes(5));
        ReflectionTestUtils.setField(existing, "id", 11L);
        when(sessionRepository.findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
                eq(1L), any(LocalDate.class), anyCollection())).thenReturn(List.of(existing));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.placeId()).isEqualTo(2L);
        verify(placeRepository, never()).findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), anyDouble(), any());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    /**
     * 서버 시간을 5분 이동해 기존 foreground 세션이 오래된 상태로 생성.
     * 기존 세션은 EXPIRED로 바뀌고 새 후보의 세션은 STARTED로 반환되어야 함.
     */
    @Test
    void replaceStaleForegroundSession() {
        VisitVerificationSession stale = VisitVerificationSession.start(1L, 2L,
                LocalDate.ofInstant(STARTED_AT, ZoneId.of("Asia/Seoul")), STARTED_AT, STARTED_AT, 15.0,
                1000.0, Duration.ofSeconds(30), Duration.ofMinutes(5));
        MapPlaceRepository.NearbyVisitPlace candidate = nearbyPlace(2L, 10.0);
        when(sessionRepository.findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
                eq(1L), any(LocalDate.class), anyCollection())).thenReturn(List.of(stale));
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(candidate));
        clock.advance(Duration.ofMinutes(5));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(stale.getStatus()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.STARTED);
    }

    /** 장소 ID와 거리만 반환하는 주변 장소 projection mock을 생성. */
    private MapPlaceRepository.NearbyVisitPlace nearbyPlace(long placeId, double distanceMeters) {
        MapPlaceRepository.NearbyVisitPlace candidate = mock(MapPlaceRepository.NearbyVisitPlace.class);
        when(candidate.getPlaceId()).thenReturn(placeId);
        when(candidate.getDistanceMeters()).thenReturn(distanceMeters);
        return candidate;
    }

    /** 세션 저장 mock의 첫 saveAndFlush 호출 인자를 찾아 후속 관측에 사용할 실제 도메인 객체를 획득. */
    private VisitVerificationSession capturedSession() {
        return (VisitVerificationSession) mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveAndFlush"))
                .findFirst().orElseThrow().getArgument(0);
    }

    /** 장소 2의 기준 좌표와 현재 테스트 시각으로 세션 시작 요청을 생성. */
    private VisitVerificationStartRequest startRequest() {
        return new VisitVerificationStartRequest(2L, 35.1801, 128.1078, 10.0, clock.instant());
    }

    /** 기준 좌표와 현재 테스트 시계의 관측 시각을 담은 후속 관측을 생성. */
    private VisitVerificationObservationRequest observationRequest() {
        return new VisitVerificationObservationRequest(35.1801, 128.1078, 10.0, clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        /** 실제 대기 없이 서버 시각 경과를 제어할 초기 시점을 보관. */
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 서버 시각을 지정 기간만큼 이동시켜 체류·만료 조건을 재현. */
        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        /** 테스트에서 시각 변환에 사용할 고정 UTC 시간대를 반환. */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** 이 fixture는 시간대 전환을 구현하지 않으므로 요청 zone과 무관하게 같은 시계를 반환. */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** 마지막으로 이동한 테스트 서버 시각을 반환. */
        @Override
        public Instant instant() {
            return instant;
        }
    }
}
