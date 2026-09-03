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

/** 체류 인증의 서버 시각 기반 완료·이탈·관측 공백과 세션별 정책 확정을 검증합니다. */
class VisitVerificationServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-08-26T06:00:00Z");
    private final VisitVerificationSessionRepository sessionRepository = mock(VisitVerificationSessionRepository.class);
    private final LocationCheckInRepository checkInRepository = mock(LocationCheckInRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final MutableClock clock = new MutableClock(STARTED_AT);
    private VisitVerificationService service;

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

    @Test
    void completesOnlyAfterServerVerifiedThirtySecondDwellWithContinuousObservations() {
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

    @Test
    void marksSessionAsProximityLostWhenAnAcceptedObservationLeavesRadius() {
        service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findByIdAndTouristUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(5));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L,
                new VisitVerificationObservationRequest(35.1851, 128.1078, 10.0, clock.instant()));

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.PROXIMITY_LOST);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsExistingSessionWithItsPersistedPolicyAfterGlobalDefaultChanges() {
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

    @Test
    void expiresSessionWhenClientMissesMaximumObservationGap() {
        service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findByIdAndTouristUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(16));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L, observationRequest());

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    @Test
    void startsForegroundSessionForTheSingleNearbyPlace() {
        MapPlaceRepository.NearbyVisitPlace candidate = nearbyPlace(2L, 10.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(candidate));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(response.placeId()).isEqualTo(2L);
        assertThat(response.requiredRadiusMeters()).isEqualTo(1000.0);
        assertThat(response.requiredDwellSeconds()).isEqualTo(30);
    }

    @Test
    void rejectsForegroundStartWhenNoPlaceIsNearby() {
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant())))
                .isInstanceOfSatisfying(VisitorVerificationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_NOT_FOUND));
    }

    @Test
    void selectsTheNearestForegroundPlaceWhenDistanceGapExceedsGpsAccuracy() {
        MapPlaceRepository.NearbyVisitPlace first = nearbyPlace(2L, 10.0);
        MapPlaceRepository.NearbyVisitPlace second = nearbyPlace(3L, 40.0);
        when(placeRepository.findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), eq(1000.0), any()))
                .thenReturn(List.of(first, second));

        VisitVerificationSessionResponse response = service.startForeground(1L,
                new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10.0, clock.instant()));

        assertThat(response.placeId()).isEqualTo(2L);
    }

    @Test
    void rejectsForegroundStartWhenGpsAccuracyCannotDistinguishNearbyPlaces() {
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

    @Test
    void rejectsForegroundStartWhenNearbyPlacesHaveTheSameDistance() {
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

    @Test
    void reusesExistingForegroundSessionBeforeSelectingAnotherNearbyPlace() {
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

    @Test
    void expiresStaleForegroundSessionBeforeSelectingANewNearbyPlace() {
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

    private MapPlaceRepository.NearbyVisitPlace nearbyPlace(long placeId, double distanceMeters) {
        MapPlaceRepository.NearbyVisitPlace candidate = mock(MapPlaceRepository.NearbyVisitPlace.class);
        when(candidate.getPlaceId()).thenReturn(placeId);
        when(candidate.getDistanceMeters()).thenReturn(distanceMeters);
        return candidate;
    }

    private VisitVerificationSession capturedSession() {
        return (VisitVerificationSession) mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveAndFlush"))
                .findFirst().orElseThrow().getArgument(0);
    }

    private VisitVerificationStartRequest startRequest() {
        return new VisitVerificationStartRequest(2L, 35.1801, 128.1078, 10.0, clock.instant());
    }

    private VisitVerificationObservationRequest observationRequest() {
        return new VisitVerificationObservationRequest(35.1801, 128.1078, 10.0, clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
