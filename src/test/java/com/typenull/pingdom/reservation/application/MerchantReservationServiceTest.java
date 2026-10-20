package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

class MerchantReservationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationStatusHistoryRepository histories = mock(ReservationStatusHistoryRepository.class);
    private final PlaceAvailabilityRepository slots = mock(PlaceAvailabilityRepository.class);
    private final PlaceAvailabilityService availability = mock(PlaceAvailabilityService.class);
    private final AvailabilityAccessPolicy access = mock(AvailabilityAccessPolicy.class);
    private final MerchantOwnerPlaceRepository owners = mock(MerchantOwnerPlaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MapPlaceRepository places = mock(MapPlaceRepository.class);
    private final ReservationQuoteService quotes = mock(ReservationQuoteService.class);
    private final ReservationService service = new ReservationService(reservations, histories, slots, availability,
            access, owners, users, places, mock(PlaceConversionEventService.class),
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), quotes);
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.create(1L, 9L, "request", 2, NOW);
        ReflectionTestUtils.setField(reservation, "id", 3L);
        when(reservations.findById(3L)).thenReturn(Optional.of(reservation));
        when(reservations.findByIdForUpdate(3L)).thenReturn(Optional.of(reservation));
        // 슬롯 생성자는 이전 점주 7이지만 현재 장소 소유자는 8이다.
        when(slots.findById(9L)).thenReturn(Optional.of(PlaceAvailability.create(
                7L, 11L, NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 10, NOW)));
        var currentOwner = MerchantOwnerPlace.builder().placeId(11L).merchantOwnerUserId(8L).createdAt(NOW).build();
        when(owners.findById(11L)).thenReturn(Optional.of(currentOwner));
        when(owners.findByPlaceIdForUpdate(11L)).thenReturn(Optional.of(currentOwner));
        when(histories.findAllByReservationIdOrderByChangedAtAscIdAsc(3L)).thenReturn(List.of());
    }

    @Test
    void currentOwnerApprovesAndRecordsNormalizedReviewWithoutReservingAgain() {
        var response = service.confirmOwned(8L, 3L, "  확인 완료  ");

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.reviewedBy()).isEqualTo(8L);
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        assertThat(response.confirmedAt()).isEqualTo(NOW);
        assertThat(response.reviewReason()).isEqualTo("확인 완료");
        assertHistory(ReservationStatus.CONFIRMED, "확인 완료");
        verify(owners).findByPlaceIdForUpdate(11L);
        verify(access).requireActiveMerchantOwner(8L, NOW);
        verifyNoInteractions(availability, quotes);
    }

    @Test
    void legacyApprovalUsesTheSameRecordedReview() {
        service.confirm(8L, 3L);
        assertThat(reservation.getReviewedBy()).isEqualTo(8L);
        assertHistory(ReservationStatus.CONFIRMED, null);
    }

    @Test
    void rejectsPendingReservationAndReleasesCapacityOnlyOnce() {
        var response = service.rejectOwned(8L, 3L, "  영업 일정 변경  ");

        assertThat(response.status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(response.reviewedBy()).isEqualTo(8L);
        assertThat(response.rejectedAt()).isEqualTo(NOW);
        assertThat(response.reviewReason()).isEqualTo("영업 일정 변경");
        assertHistory(ReservationStatus.REJECTED, "영업 일정 변경");
        assertError(() -> service.rejectOwned(8L, 3L, "중복"), ReservationErrorCode.INVALID_RESERVATION_STATE);
        assertError(() -> service.confirmOwned(8L, 3L, null), ReservationErrorCode.INVALID_RESERVATION_STATE);
        verify(availability, times(1)).release(9L, 2);
        verify(histories, times(1)).save(any());
        verifyNoInteractions(quotes);
    }

    @Test
    void approvedReservationCannotBeReviewedAgain() {
        service.confirmOwned(8L, 3L, null);
        assertError(() -> service.confirmOwned(8L, 3L, null), ReservationErrorCode.INVALID_RESERVATION_STATE);
        assertError(() -> service.rejectOwned(8L, 3L, "중복"), ReservationErrorCode.INVALID_RESERVATION_STATE);
        verify(histories, times(1)).save(any());
        verifyNoInteractions(availability);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectionRequiresAReasonBeforeChangingState(String reason) {
        assertError(() -> service.rejectOwned(8L, 3L, reason), ReservationErrorCode.INVALID_RESERVATION_INPUT);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(availability, histories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "reject", "cancel", "get"})
    void previousOwnerCannotAccessTransferredReservation(String operation) {
        assertError(() -> invoke(operation, 7L), ReservationErrorCode.RESERVATION_FORBIDDEN);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(access, availability, histories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "reject", "cancel", "get"})
    void inactiveOwnerCannotReadOrChangeReservation(String operation) {
        doThrow(new AvailabilityException(AvailabilityErrorCode.PLACE_NOT_OWNED))
                .when(access).requireActiveMerchantOwner(8L, NOW);
        assertThatThrownBy(() -> invoke(operation, 8L)).isInstanceOf(AvailabilityException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(availability, histories);
    }

    @Test
    void detailContainsOnlyTheOwnedReservationAndItsHistory() {
        var prior = ReservationStatusHistory.of(3L, ReservationStatus.PENDING, 1L, null, NOW.minusMinutes(5));
        when(histories.findAllByReservationIdOrderByChangedAtAscIdAsc(3L)).thenReturn(List.of(prior));

        var response = service.getOwned(8L, 3L);

        assertThat(response.placeId()).isEqualTo(11L);
        assertThat(response.reservation().id()).isEqualTo(3L);
        assertThat(response.statusHistory()).singleElement().satisfies(history -> {
            assertThat(history.changedBy()).isEqualTo(1L);
            assertThat(history.status()).isEqualTo(ReservationStatus.PENDING);
        });
        verify(owners, never()).findByPlaceIdForUpdate(anyLong());
    }

    @Test
    void ownedListAlwaysScopesFiltersToAuthenticatedOwner() {
        when(reservations.findAllOwned(eq(8L), eq(ReservationStatus.PENDING), eq(11L),
                eq(true), eq(NOW), eq(true), eq(NOW.plusDays(1)), any())).thenReturn(Page.empty());

        var response = service.listOwned(8L, ReservationStatus.PENDING, 11L, NOW, NOW.plusDays(1), 1, 20);

        assertThat(response.totalElements()).isZero();
        verify(reservations).findAllOwned(eq(8L), eq(ReservationStatus.PENDING), eq(11L),
                eq(true), eq(NOW), eq(true), eq(NOW.plusDays(1)), any());
        verify(access).requireActiveMerchantOwner(8L, NOW);
    }

    @Test
    void ownedListDisablesAbsentPeriodFilters() {
        when(reservations.findAllOwned(eq(8L), isNull(), isNull(), eq(false), isNull(),
                eq(false), isNull(), any())).thenReturn(Page.empty());
        service.listOwned(8L, null, null, null, null, 1, 20);
        verify(reservations).findAllOwned(eq(8L), isNull(), isNull(), eq(false), isNull(), eq(false), isNull(), any());
    }

    @Test
    void rejectsInvalidPeriodBeforeQuerying() {
        assertError(() -> service.listOwned(8L, null, null, NOW, NOW, 1, 20),
                ReservationErrorCode.INVALID_RESERVATION_INPUT);
        verifyNoInteractions(reservations);
    }

    @Test
    void adminReadKeepsHistoricalReviewerAndShowsCurrentOwner() {
        reservation.confirm(25L, "기존 관리자 승인", NOW.minusMinutes(1));
        var history = ReservationStatusHistory.of(3L, ReservationStatus.CONFIRMED, 25L,
                "기존 관리자 승인", NOW.minusMinutes(1));
        when(histories.findAllByReservationIdOrderByChangedAtAscIdAsc(3L)).thenReturn(List.of(history));
        when(users.findById(8L)).thenReturn(Optional.of(User.builder().id(8L).username("현재 점주").build()));
        when(reservations.findAllForAdmin(isNull(), isNull(), eq(8L), isNull(), eq(false), isNull(),
                eq(false), isNull(), any())).thenReturn(new PageImpl<>(List.of(reservation)));

        var response = service.listForAdmin(null, null, 8L, null, null, null, 1, 20).reservations().getFirst();

        assertThat(response.merchantOwnerUserId()).isEqualTo(8L);
        assertThat(response.merchantOwnerUsername()).isEqualTo("현재 점주");
        assertThat(response.reviewedBy()).isEqualTo(25L);
        assertThat(response.statusHistory()).singleElement().satisfies(item ->
                assertThat(item.changedBy()).isEqualTo(25L));
        verify(users, never()).findById(7L);
    }

    private void invoke(String operation, Long ownerId) {
        switch (operation) {
            case "confirm" -> service.confirmOwned(ownerId, 3L, null);
            case "reject" -> service.rejectOwned(ownerId, 3L, "사유");
            case "cancel" -> service.cancelOwned(ownerId, 3L);
            case "get" -> service.getOwned(ownerId, 3L);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void assertHistory(ReservationStatus status, String reason) {
        var captor = ArgumentCaptor.forClass(ReservationStatusHistory.class);
        verify(histories).save(captor.capture());
        assertThat(captor.getValue()).satisfies(history -> {
            assertThat(history.getReservationId()).isEqualTo(3L);
            assertThat(history.getStatus()).isEqualTo(status);
            assertThat(history.getChangedBy()).isEqualTo(8L);
            assertThat(history.getReason()).isEqualTo(reason);
            assertThat(history.getChangedAt()).isEqualTo(NOW);
        });
    }

    private void assertError(Runnable action, ReservationErrorCode error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ReservationException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(error));
    }
}
