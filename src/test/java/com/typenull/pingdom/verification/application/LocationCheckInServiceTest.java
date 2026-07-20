package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.LocationCheckInRequest;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import java.time.*;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationCheckInServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T06:00:00Z");
    private final LocationCheckInRepository checkInRepository = mock(LocationCheckInRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private LocationCheckInService service;

    @BeforeEach
    void setUp() {
        service = new LocationCheckInService(checkInRepository, userRepository, placeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), new LocationCheckInProperties(
                        100.0, 50.0, Duration.ofMinutes(5), Duration.ofSeconds(30)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        when(placeRepository.findById(2L)).thenReturn(Optional.of(MapPlace.builder()
                .id(2L).name("테스트 장소").address("주소").latitude(35.1801).longitude(128.1078)
                .registrant("등록자").build()));
        when(checkInRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void verifiesCheckInWithinRadiusWithFreshAccurateLocation() {
        var response = service.checkIn(1L, request(35.1802, 128.1078, 10, NOW.minusSeconds(30)));

        assertThat(response.status()).isEqualTo(LocationCheckInStatus.PROXIMITY_MATCHED);
        assertThat(response.checkInDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(response.distanceMeters()).isBetween(10.0, 12.0);
        verify(checkInRepository).saveAndFlush(any(LocationCheckIn.class));
    }

    @Test
    void rejectsLocationOutsideOneHundredMeterRadius() {
        assertError(request(35.1820, 128.1078, 10, NOW), VisitorVerificationErrorCode.OUTSIDE_CHECK_IN_RADIUS);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsObservationOlderThanFiveMinutes() {
        assertError(request(35.1801, 128.1078, 10, NOW.minusSeconds(301)),
                VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
    }

    @Test
    void rejectsAccuracyWorseThanFiftyMeters() {
        assertError(request(35.1801, 128.1078, 50.1, NOW),
                VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
    }

    @Test
    void acceptsExactAccuracyAndObservationAgeBoundaries() {
        var response = service.checkIn(1L, request(35.1801, 128.1078, 50, NOW.minusSeconds(300)));

        assertThat(response.status()).isEqualTo(LocationCheckInStatus.PROXIMITY_MATCHED);
    }

    @Test
    void rejectsObservationBeyondFutureTolerance() {
        assertError(request(35.1801, 128.1078, 10, NOW.plusSeconds(31)),
                VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
    }

    @Test
    void hidesUnavailablePlaceFromCheckIn() {
        when(placeRepository.findById(2L)).thenReturn(Optional.of(MapPlace.builder()
                .id(2L).name("숨김 장소").address("주소").latitude(35.1801).longitude(128.1078)
                .discoveryStatus(PlaceDiscoveryStatus.HIDDEN)
                .registrant("등록자").build()));

        assertError(request(35.1801, 128.1078, 10, NOW), VisitorVerificationErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    void rejectsSecondVerifiedCheckInAtSamePlaceOnSameDay() {
        when(checkInRepository.existsByTouristUserIdAndPlaceIdAndCheckInDate(
                1L, 2L, LocalDate.of(2026, 7, 20))).thenReturn(true);

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
    }

    @Test
    void mapsDatabaseDailyUniqueConstraintRaceToConflict() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_location_check_in_daily");
        when(checkInRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate", constraint));

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
    }

    @Test
    void rejectsNonTouristAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        verify(placeRepository, never()).findById(anyLong());
    }

    private LocationCheckInRequest request(double latitude, double longitude, double accuracy, Instant observedAt) {
        return new LocationCheckInRequest(2L, latitude, longitude, accuracy, observedAt);
    }

    private void assertError(LocationCheckInRequest request, VisitorVerificationErrorCode errorCode) {
        assertThatThrownBy(() -> service.checkIn(1L, request))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
