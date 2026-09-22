package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.reservation.api.dto.ReservationCreateRequest;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.exception.ReservationException;
import com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class ReservationServiceTest {
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final PlaceAvailabilityRepository availabilityRepository = mock(PlaceAvailabilityRepository.class);
    private final PlaceAvailabilityService availabilityService = mock(PlaceAvailabilityService.class);
    private final AvailabilityAccessPolicy availabilityAccessPolicy = mock(AvailabilityAccessPolicy.class);
    private final MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceConversionEventService conversionEventService = mock(PlaceConversionEventService.class);
    private ReservationService service;

    /**
     * 사용자 1의 일반/잠금 조회를 활성 관광객으로 고정하고 예약 저장·슬롯 예약 결과와 UTC Clock을 구성.
     */
    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, availabilityRepository, availabilityService,
                availabilityAccessPolicy, ownerPlaceRepository, userRepository,
                conversionEventService,
                Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC));
        User tourist = User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(tourist));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        when(availabilityService.reserve(anyLong(), anyInt())).thenReturn(PlaceAvailability.create(
                7L, 11L, AvailabilityProductType.GENERAL,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now));
    }

    /**
     * 수량 2 예약 생성이 슬롯 정원 차감·예약 저장·장소 전환 이벤트 발행을 호출하고 PENDING·GENERAL 응답을 반환하는지 검증.
     */
    @Test
    void createsPendingReservation() {
        var response = service.create(1L, new ReservationCreateRequest(9L, "request-1", 2));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.productType()).isEqualTo(AvailabilityProductType.GENERAL);
        verify(availabilityService).reserve(9L, 2);
        verify(reservationRepository).save(any(Reservation.class));
        verify(conversionEventService).publish(eq(1L), eq(11L), any(), isNull(), any());
    }

    /**
     * 티켓 슬롯의 상품 ID·유형·시작/종료 시각과 예약자 이름·전화·요청사항이 응답에 반영되고, 저장 대상의 상품 유형이 TICKET인지 검증.
     */
    @Test
    void snapshotsTicketReservationDetails() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        when(availabilityService.reserve(9L, 2)).thenReturn(PlaceAvailability.create(
                7L, 11L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now));

        var response = service.create(1L, new ReservationCreateRequest(
                9L, "ticket-request", 2, "홍길동", "010-1234-5678", "창가 자리 부탁드립니다."));

        assertThat(response.productType()).isEqualTo(AvailabilityProductType.TICKET);
        assertThat(response.productId()).isEqualTo(31L);
        assertThat(response.reservationStartsAt()).isEqualTo(now.plusDays(1));
        assertThat(response.reservationEndsAt()).isEqualTo(now.plusDays(1).plusHours(1));
        assertThat(response.bookerName()).isEqualTo("홍길동");
        assertThat(response.bookerPhone()).isEqualTo("010-1234-5678");
        assertThat(response.requestNote()).isEqualTo("창가 자리 부탁드립니다.");
        verify(reservationRepository).save(argThat(reservation ->
                reservation.getProductType() == AvailabilityProductType.TICKET));
    }

    /**
     * 확정 예약을 취소하면 수량 2를 반환하고 반복 취소는 예외가 되어 정원을 한 번만 복구하는지 검증.
     */
    @Test
    void cancelReleasesCapacityOnlyOnce() {
        Reservation reservation = Reservation.create(1L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        reservation.confirm(LocalDateTime.of(2026, 7, 20, 13, 5));
        when(reservationRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reservation));

        service.cancelMine(1L, 3L);

        verify(availabilityService).release(9L, 2);
        assertThatThrownBy(() -> service.cancelMine(1L, 3L)).isInstanceOf(ReservationException.class);
        verify(availabilityService, times(1)).release(9L, 2);
    }

    /**
     * 같은 사용자·멱등 키의 동일 예약 요청이 있으면 슬롯 예약과 추가 저장을 실행하지 않는지 검증.
     */
    @Test
    void reusesIdempotentReservation() {
        Reservation existing = Reservation.create(1L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findByTouristUserIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(existing));

        service.create(1L, new ReservationCreateRequest(9L, "request-1", 2));

        verifyNoInteractions(availabilityService);
        verify(reservationRepository, never()).save(any());
    }

    /**
     * 같은 멱등 키로 다른 슬롯을 요청하면 IDEMPOTENCY_KEY_REUSED를 반환하고 정원 처리에 도달하지 않는지 검증.
     */
    @Test
    void rejectsIdempotentSlotMismatch() {
        Reservation existing = Reservation.create(1L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findByTouristUserIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, new ReservationCreateRequest(10L, "request-1", 2)))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));

        verifyNoInteractions(availabilityService);
    }

    /**
     * 같은 키·슬롯·수량이어도 예약자 이름이 바뀌면 IDEMPOTENCY_KEY_REUSED를 반환하고 정원 처리에 도달하지 않는지 검증.
     */
    @Test
    void rejectsIdempotentBookerMismatch() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        Reservation existing = Reservation.create(1L, 9L, null, AvailabilityProductType.GENERAL,
                "request-1", 2, now.plusDays(1), now.plusDays(1).plusHours(1),
                "홍길동", "010-1234-5678", null, now);
        when(reservationRepository.findByTouristUserIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, new ReservationCreateRequest(
                9L, "request-1", 2, "김길동", "010-1234-5678", null)))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));

        verifyNoInteractions(availabilityService);
    }

    /**
     * 사용자 1이 관광객 2의 예약을 단건 조회하면 RESERVATION_FORBIDDEN인지 검증.
     */
    @Test
    void rejectsAnotherTouristReservation() {
        Reservation reservation = Reservation.create(2L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.getMine(1L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));
    }

    /**
     * 없는 예약을 단건 조회하면 RESERVATION_NOT_FOUND인지 검증.
     */
    @Test
    void rejectsUnknownReservation() {
        when(reservationRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(1L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    /**
     * 예약 소유자라도 MERCHANT_OWNER 계정이면 내 예약 조회에서 TOURIST_ACCOUNT_REQUIRED인지 검증.
     */
    @Test
    void getMineRequiresTouristAccount() {
        Reservation reservation = Reservation.create(2L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        User merchantOwner = User.builder().id(2L).role(UserRole.MERCHANT_OWNER).status(UserStatus.ACTIVE).build();
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(2L)).thenReturn(Optional.of(merchantOwner));

        assertThatThrownBy(() -> service.getMine(2L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReservationErrorCode.TOURIST_ACCOUNT_REQUIRED));
    }

    /**
     * 관리자 목록의 예약 기간 값이 모두 없으면 저장소의 두 기간 적용 플래그를 false로 전달하는지 검증.
     */
    @Test
    void disablesAbsentReservationPeriodFilters() {
        when(reservationRepository.findAllForAdmin(eq(ReservationStatus.PENDING), isNull(), isNull(), isNull(),
                isNull(), eq(false), isNull(), eq(false), isNull(), any()))
                .thenReturn(Page.empty());

        service.listForAdmin(ReservationStatus.PENDING, null, null, null, null, null, null, 1, 10);

        verify(reservationRepository).findAllForAdmin(eq(ReservationStatus.PENDING), isNull(), isNull(), isNull(),
                isNull(), eq(false), isNull(), eq(false), isNull(), any());
    }

    /**
     * 이전 점주 슬롯의 예약도 현재 소유자를 잠금 조회하여 확인하고 활성 점주 8이 CONFIRMED로 전이시킬 수 있는지 검증.
     */
    @Test
    void currentOwnerConfirmsTransferredReservation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        Reservation reservation = Reservation.create(1L, 9L, "request-1", 2, now);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 11L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(reservationRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reservation));
        when(availabilityRepository.findById(9L)).thenReturn(Optional.of(availability));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(11L)).thenReturn(Optional.of(MerchantOwnerPlace.builder()
                .placeId(11L).merchantOwnerUserId(8L).createdAt(now).build()));

        service.confirm(8L, 3L);

        verify(ownerPlaceRepository).findByPlaceIdForUpdate(11L);
        verify(availabilityAccessPolicy).requireActiveMerchantOwner(eq(8L), any(LocalDateTime.class));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    /**
     * 장소가 점주 8로 이전된 뒤 이전 점주 7의 확정 요청은 RESERVATION_FORBIDDEN이며 예약은 PENDING으로 유지되는지 검증.
     * 활성 점주 정책 미호출도 확인해 현재 소유권 검증 경계를 고정.
     */
    @Test
    void rejectsPreviousOwnerConfirmation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        Reservation reservation = Reservation.create(1L, 9L, "request-1", 2, now);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 11L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(reservationRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reservation));
        when(availabilityRepository.findById(9L)).thenReturn(Optional.of(availability));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(11L)).thenReturn(Optional.of(MerchantOwnerPlace.builder()
                .placeId(11L).merchantOwnerUserId(8L).createdAt(now).build()));

        assertThatThrownBy(() -> service.confirm(7L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));

        verifyNoInteractions(availabilityAccessPolicy);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
}
