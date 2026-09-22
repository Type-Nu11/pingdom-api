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

/** 위치·반경·중복 조건을 포함한 방문 인증 서비스 시나리오를 검증합니다. */
class LocationCheckInServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T06:00:00Z");
    private final LocationCheckInRepository checkInRepository = mock(LocationCheckInRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private LocationCheckInService service;

    /**
     * 시각과 반경 100m·정확도 50m·TTL 5분을 고정한다.
     * 활성 일반 사용자와 좌표가 알려진 장소를 제공하고 저장 mock은 입력 체크인을 그대로 반환한다.
     */
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

    /**
     * 30초 전의 정확도 10m 관측이 근접 인증 상태와 서울 기준 2026-07-20 날짜로 저장되는지 확인한다.
     * 위도 차이로 계산한 거리는 10~12m여야 하며 flush 저장을 호출해야 한다.
     */
    @Test
    void acceptNearbyObservation() {
        var response = service.checkIn(1L, request(35.1802, 128.1078, 10, NOW.minusSeconds(30)));

        assertThat(response.status()).isEqualTo(LocationCheckInStatus.PROXIMITY_MATCHED);
        assertThat(response.checkInDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(response.distanceMeters()).isBetween(10.0, 12.0);
        verify(checkInRepository).saveAndFlush(any(LocationCheckIn.class));
    }

    /** 100m 반경 밖의 위치는 반경 초과 오류로 거부하고 DB 저장을 호출하지 않아야 한다. */
    @Test
    void rejectOutsideRadius() {
        assertError(request(35.1820, 128.1078, 10, NOW), VisitorVerificationErrorCode.OUTSIDE_CHECK_IN_RADIUS);
        verify(checkInRepository, never()).saveAndFlush(any());
    }

    /** 관측 TTL 300초를 1초 넘긴 관측은 만료 오류로 거부한다. */
    @Test
    void rejectStaleObservation() {
        assertError(request(35.1801, 128.1078, 10, NOW.minusSeconds(301)),
                VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
    }

    /** 허용 정확도 50m보다 0.1m 큰 값은 위치 부정확 오류로 거부한다. */
    @Test
    void rejectPoorAccuracy() {
        assertError(request(35.1801, 128.1078, 50.1, NOW),
                VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE);
    }

    /** 정확도 50m와 관측 나이 300초의 정확한 상한을 동시에 만족하면 근접 체크인을 허용한다. */
    @Test
    void acceptObservationBoundaries() {
        var response = service.checkIn(1L, request(35.1801, 128.1078, 50, NOW.minusSeconds(300)));

        assertThat(response.status()).isEqualTo(LocationCheckInStatus.PROXIMITY_MATCHED);
    }

    /** 미래 허용 오차 30초보다 1초 먼 관측 시각은 만료 오류로 처리한다. */
    @Test
    void rejectFutureObservation() {
        assertError(request(35.1801, 128.1078, 10, NOW.plusSeconds(31)),
                VisitorVerificationErrorCode.LOCATION_OBSERVATION_EXPIRED);
    }

    /** HIDDEN 장소는 조회되더라도 PLACE_NOT_FOUND로 거부해 체크인 대상에서 제외한다. */
    @Test
    void hideUnavailablePlace() {
        when(placeRepository.findById(2L)).thenReturn(Optional.of(MapPlace.builder()
                .id(2L).name("숨김 장소").address("주소").latitude(35.1801).longitude(128.1078)
                .discoveryStatus(PlaceDiscoveryStatus.HIDDEN)
                .registrant("등록자").build()));

        assertError(request(35.1801, 128.1078, 10, NOW), VisitorVerificationErrorCode.PLACE_NOT_FOUND);
    }

    /** 같은 사용자·장소·서울 날짜에 기존 기록이 있으면 일일 중복 오류로 거부한다. */
    @Test
    void rejectDailyDuplicate() {
        when(checkInRepository.existsByTouristUserIdAndPlaceIdAndCheckInDate(
                1L, 2L, LocalDate.of(2026, 7, 20))).thenReturn(true);

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
    }

    /**
     * 사전 중복 조회를 통과한 뒤 DB flush가 일일 유일 제약 위반으로 실패하는 상황을 구성한다.
     * 경합으로 발생한 중복도 동일한 DAILY_CHECK_IN_ALREADY_EXISTS 오류로 전달해야 한다.
     */
    @Test
    void mapDailyConstraintConflict() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_location_check_in_daily");
        when(checkInRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate", constraint));

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS);
    }

    /** ADMIN 계정은 관광객 계정 필요 오류로 거부하고 장소 조회까지 진행하지 않아야 한다. */
    @Test
    void rejectNonTouristAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));

        assertError(request(35.1801, 128.1078, 10, NOW),
                VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        verify(placeRepository, never()).findById(anyLong());
    }

    /** 장소 2를 대상으로 좌표·정확도·관측 시각을 주입한 요청을 만든다. */
    private LocationCheckInRequest request(double latitude, double longitude, double accuracy, Instant observedAt) {
        return new LocationCheckInRequest(2L, latitude, longitude, accuracy, observedAt);
    }

    /** 사용자 1의 체크인 요청이 지정한 방문 인증 오류 코드로 실패하는지 확인한다. */
    private void assertError(LocationCheckInRequest request, VisitorVerificationErrorCode errorCode) {
        assertThatThrownBy(() -> service.checkIn(1L, request))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
