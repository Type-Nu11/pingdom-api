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

    @Test
    void createReservesCapacityAndStartsPending() {
        var response = service.create(1L, new ReservationCreateRequest(9L, "request-1", 2));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.productType()).isEqualTo(AvailabilityProductType.GENERAL);
        verify(availabilityService).reserve(9L, 2);
        verify(reservationRepository).save(any(Reservation.class));
        verify(conversionEventService).publish(eq(1L), eq(11L), any(), isNull(), any());
    }

    @Test
    void createSnapshotsTicketProductTypeFromAvailability() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 13, 0);
        when(availabilityService.reserve(9L, 2)).thenReturn(PlaceAvailability.create(
                7L, 11L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now));

        var response = service.create(1L, new ReservationCreateRequest(9L, "ticket-request", 2));

        assertThat(response.productType()).isEqualTo(AvailabilityProductType.TICKET);
        assertThat(response.productId()).isEqualTo(31L);
        verify(reservationRepository).save(argThat(reservation ->
                reservation.getProductType() == AvailabilityProductType.TICKET));
    }

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

    @Test
    void repeatedIdempotencyKeyReturnsExistingReservationWithoutReservingAgain() {
        Reservation existing = Reservation.create(1L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findByTouristUserIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(existing));

        service.create(1L, new ReservationCreateRequest(9L, "request-1", 2));

        verifyNoInteractions(availabilityService);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        Reservation existing = Reservation.create(1L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findByTouristUserIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(1L, new ReservationCreateRequest(10L, "request-1", 2)))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));

        verifyNoInteractions(availabilityService);
    }

    @Test
    void getMineRejectsAnotherTouristReservation() {
        Reservation reservation = Reservation.create(2L, 9L, "request-1", 2,
                LocalDateTime.of(2026, 7, 20, 13, 0));
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.getMine(1L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));
    }

    @Test
    void getMineReturnsNotFoundForUnknownReservation() {
        when(reservationRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(1L, 3L))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

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

    @Test
    void adminListMarksAbsentReservationPeriodFiltersAsDisabled() {
        when(reservationRepository.findAllForAdmin(eq(ReservationStatus.PENDING), isNull(), isNull(), isNull(),
                isNull(), eq(false), isNull(), eq(false), isNull(), any()))
                .thenReturn(Page.empty());

        service.listForAdmin(ReservationStatus.PENDING, null, null, null, null, null, null, 1, 10);

        verify(reservationRepository).findAllForAdmin(eq(ReservationStatus.PENDING), isNull(), isNull(), isNull(),
                isNull(), eq(false), isNull(), eq(false), isNull(), any());
    }

    @Test
    void currentPlaceOwnerCanConfirmReservationCreatedBeforeOwnershipTransfer() {
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

    @Test
    void previousOwnerCannotConfirmAfterOwnershipTransfer() {
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
