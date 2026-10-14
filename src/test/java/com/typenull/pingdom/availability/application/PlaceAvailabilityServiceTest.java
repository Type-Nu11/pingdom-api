package com.typenull.pingdom.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PlaceAvailabilityServiceTest {
    private final PlaceAvailabilityRepository repository = mock(PlaceAvailabilityRepository.class);
    private final AvailabilityAccessPolicy accessPolicy = mock(AvailabilityAccessPolicy.class);
    private PlaceAvailabilityService service;

    @Test
    void termsAreExplicitAndChangeOnlyTheConditionsRevision() {
        var slot = PlaceAvailability.create(7L, 11L, LocalDateTime.of(2026, 7, 21, 13, 0),
                LocalDateTime.of(2026, 7, 21, 14, 0), 10, LocalDateTime.of(2026, 7, 20, 13, 0));
        when(repository.findByIdForUpdate(9L)).thenReturn(java.util.Optional.of(slot));
        var terms = new com.typenull.pingdom.availability.api.dto.ReservationTermsRequest("Asia/Seoul", false, null);
        assertThat(service.updateReservationTerms(7L, 9L, terms).timezone()).isEqualTo("Asia/Seoul");
        assertThat(slot.getConditionsVersion()).isEqualTo(1);
        assertThat(slot.getRemainingCapacity()).isEqualTo(10);
        assertThatThrownBy(() -> service.updateReservationTerms(8L, 9L, terms))
                .isInstanceOfSatisfying(AvailabilityException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AvailabilityErrorCode.PLACE_NOT_OWNED));
        assertThatThrownBy(() -> service.updateReservationTerms(7L, 9L,
                new com.typenull.pingdom.availability.api.dto.ReservationTermsRequest("UTC", true, null)))
                .isInstanceOfSatisfying(AvailabilityException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT));
    }

    /**
     * UTC 고정 Clock과 저장소·접근 정책 mock을 사용해 시간 조건이 재현되는 서비스를 구성.
     */
    @BeforeEach
    void setUp() {
        service = new PlaceAvailabilityService(repository, accessPolicy, Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * 소유 슬롯 목록 조회 시 활성 가맹점 확인과 현재 소유 목록 저장소 조회가 실행되는지 검증.
     * 과거 소유권만으로 목록을 제공하는 회귀를 방지.
     */
    @Test
    void checksOwnedListAccess() {
        when(repository.findAllCurrentlyOwned(7L)).thenReturn(List.of());

        service.listOwned(7L);

        verify(accessPolicy).requireActiveMerchantOwner(eq(7L), any(LocalDateTime.class));
        verify(repository).findAllCurrentlyOwned(7L);
    }

    /**
     * 상점주 재접속 후에도 저장된 취소 불가 조건과 미설정 상태를 구분해 편집 화면에 전달한다.
     * 조건 버전은 조건이 없는 신규 슬롯에도 포함해 이후 변경 감지 기준으로 사용한다.
     */
    @Test
    void returnsStoredTermsAndKeepsUnconfiguredTermsNull() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability configured = PlaceAvailability.create(
                7L, 3L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        configured.updateReservationTerms(new com.typenull.pingdom.availability.domain.ReservationTerms("Asia/Seoul", false, null), now);
        PlaceAvailability unconfigured = PlaceAvailability.create(
                7L, 3L, now.plusDays(2), now.plusDays(2).plusHours(1), 10, now);
        when(repository.findAllCurrentlyOwned(7L)).thenReturn(List.of(configured, unconfigured));

        List<AvailabilityResponse> responses = service.listOwned(7L);

        assertThat(responses.get(0).conditionsVersion()).isEqualTo(1);
        assertThat(responses.get(0).reservationTerms())
                .extracting("timezone", "cancellable", "cancellationCutoffMinutes")
                .containsExactly("Asia/Seoul", false, null);
        assertThat(responses.get(1).conditionsVersion()).isZero();
        assertThat(responses.get(1).reservationTerms()).isNull();
    }


    /**
     * 슬롯 고유 제약 위반으로 생성 저장이 실패하면 AVAILABILITY_ALREADY_EXISTS로 변환되는지 검증.
     * DB 예외가 클라이언트의 중복 예약 시간 오류 계약을 우회하는 회귀를 방지.
     */
    @Test
    void mapsDuplicateSlotCreation() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 11, 0),
                10
        );

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS));
    }

    /**
     * 기존 슬롯 수정의 flush에서 고유 제약 위반이 발생하면 AVAILABILITY_ALREADY_EXISTS로 변환되는지 검증.
     */
    @Test
    void mapsDuplicateSlotUpdate() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        doThrow(duplicateSlotViolation()).when(repository).flush();
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );

        assertThatThrownBy(() -> service.update(7L, 9L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS));
    }





    /**
     * 예약 가능한 슬롯의 잠금 조회가 비어 있으면 AVAILABILITY_NOT_FOUND가 발생하는지 검증.
     * 소유자 조건 등을 만족하지 않는 슬롯으로 예약이 진행되는 회귀를 방지.
     */
    @Test
    void rejectsUnavailableReservation() {
        when(repository.findReservableByIdForUpdate(eq(9L), any(LocalDateTime.class)))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.reserve(9L, 1))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
    }



    /**
     * 슬롯 고유 제약 이름을 담은 Hibernate 예외를 Spring 무결성 예외로 감쌈.
     * 수정 flush 실패가 실제 중복 제약 판별 경로를 통과하도록 구성.
     */
    private DataIntegrityViolationException duplicateSlotViolation() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        return new DataIntegrityViolationException("duplicate", constraint);
    }

}
