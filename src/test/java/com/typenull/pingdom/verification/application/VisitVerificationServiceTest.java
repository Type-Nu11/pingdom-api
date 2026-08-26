package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 체류 인증의 서버 시각 기반 완료·이탈·관측 공백 처리를 검증합니다. */
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
        VisitVerificationProperties properties = new VisitVerificationProperties(20.0, Map.of(), 20.0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofDays(30));
        service = new VisitVerificationService(sessionRepository, checkInRepository, userRepository, placeRepository,
                clock, properties);
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
        when(sessionRepository.findLockedByIdAndTouristUserId(10L, 1L)).thenReturn(Optional.of(session));

        clock.advance(Duration.ofSeconds(15));
        VisitVerificationSessionResponse inProgress = service.submitObservation(1L, 10L, observationRequest());
        clock.advance(Duration.ofSeconds(15));
        VisitVerificationSessionResponse completed = service.submitObservation(1L, 10L, observationRequest());

        assertThat(started.status()).isEqualTo(VisitVerificationSessionStatus.STARTED);
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
        when(sessionRepository.findLockedByIdAndTouristUserId(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(5));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L,
                new VisitVerificationObservationRequest(35.1810, 128.1078, 10.0, clock.instant()));

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.PROXIMITY_LOST);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiresSessionWhenClientMissesMaximumObservationGap() {
        service.start(1L, startRequest());
        VisitVerificationSession session = capturedSession();
        when(sessionRepository.findLockedByIdAndTouristUserId(10L, 1L)).thenReturn(Optional.of(session));
        clock.advance(Duration.ofSeconds(16));

        VisitVerificationSessionResponse response = service.submitObservation(1L, 10L, observationRequest());

        assertThat(response.status()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        verify(checkInRepository, never()).saveAndFlush(any());
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
