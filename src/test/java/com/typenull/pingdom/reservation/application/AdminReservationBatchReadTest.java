package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode;
import com.typenull.pingdom.reservation.domain.exception.ReservationException;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import com.typenull.pingdom.reservation.infrastructure.ReservationStatusHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

class AdminReservationBatchReadTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationStatusHistoryRepository histories = mock(ReservationStatusHistoryRepository.class);
    private final PlaceAvailabilityRepository slots = mock(PlaceAvailabilityRepository.class);
    private final MerchantOwnerPlaceRepository owners = mock(MerchantOwnerPlaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MapPlaceRepository places = mock(MapPlaceRepository.class);
    private final ReservationService service = new ReservationService(reservations, histories, slots,
            mock(PlaceAvailabilityService.class), mock(AvailabilityAccessPolicy.class), owners, users, places,
            mock(PlaceConversionEventService.class), Clock.systemUTC(), mock(ReservationQuoteService.class));

    @Test
    void batchesRelationsWithoutChangingPageOrderFiltersOrHistoricalActors() {
        Reservation first = reservation(21L, 1L, 11L);
        Reservation second = reservation(22L, 2L, 12L);
        first.confirm(25L, "과거 심사", NOW);
        PageRequest pageRequest = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(reservations.findAllForAdmin(isNull(), eq(101L), eq(8L), eq(1L), eq(true), eq(NOW),
                eq(true), eq(NOW.plusDays(1)), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(second, first), pageRequest, 5));
        when(slots.findAllById(List.of(12L, 11L)))
                .thenReturn(List.of(slot(11L, 101L), slot(12L, 102L)));
        when(owners.findAllByPlaceIdIn(any())).thenReturn(List.of(owner(101L, 8L), owner(102L, 9L)));
        when(users.findAllById(Set.of(1L, 2L, 8L, 9L)))
                .thenReturn(List.of(user(9L, "점주2"), user(1L, "예약자1"), user(8L, "점주1"), user(2L, "예약자2")));
        when(places.findAllById(any())).thenReturn(List.of(place(101L, "장소1"), place(102L, "장소2")));
        when(histories.findAllByReservationIdInOrderByReservationIdAscChangedAtAscIdAsc(List.of(22L, 21L)))
                .thenReturn(List.of(history(21L, 25L, "첫 이력"), history(21L, 26L, "동시각 다음 이력"),
                        history(22L, 27L, "다른 예약")));

        var result = service.listForAdmin(null, 101L, 8L, 1L, NOW, NOW.plusDays(1), 2, 2);

        assertThat(result.reservations()).extracting(item -> item.id()).containsExactly(22L, 21L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        var firstResponse = result.reservations().get(1);
        assertThat(firstResponse.touristUsername()).isEqualTo("예약자1");
        assertThat(firstResponse.merchantOwnerUserId()).isEqualTo(8L);
        assertThat(firstResponse.merchantOwnerUsername()).isEqualTo("점주1");
        assertThat(firstResponse.placeName()).isEqualTo("장소1");
        assertThat(firstResponse.reviewedBy()).isEqualTo(25L);
        assertThat(firstResponse.statusHistory()).extracting(item -> item.changedBy()).containsExactly(25L, 26L);
        assertThat(result.reservations().getFirst().statusHistory()).extracting(item -> item.changedBy())
                .containsExactly(27L);
        verify(reservations).findAllForAdmin(null, 101L, 8L, 1L, true, NOW, true, NOW.plusDays(1), pageRequest);
        verify(slots).findAllById(List.of(12L, 11L));
        verify(owners).findAllByPlaceIdIn(any());
        verify(users).findAllById(Set.of(1L, 2L, 8L, 9L));
        verify(places).findAllById(any());
        verify(histories).findAllByReservationIdInOrderByReservationIdAscChangedAtAscIdAsc(List.of(22L, 21L));
        verify(slots, never()).findById(any());
        verify(owners, never()).findById(any());
        verify(users, never()).findById(any());
        verify(places, never()).findById(any());
        verify(histories, never()).findAllByReservationIdOrderByChangedAtAscIdAsc(any());
    }

    @Test
    void deduplicatesSharedRelationIdsAndKeepsEmptyHistoryAsEmptyList() {
        stubPage(List.of(reservation(21L, 1L, 11L), reservation(22L, 1L, 11L)));
        when(slots.findAllById(List.of(11L))).thenReturn(List.of(slot(11L, 101L)));
        when(owners.findAllByPlaceIdIn(List.of(101L))).thenReturn(List.of(owner(101L, 1L)));
        when(users.findAllById(Set.of(1L))).thenReturn(List.of(user(1L, "예약자 겸 점주")));

        var result = service.listForAdmin(null, null, null, null, null, null, 1, 20);

        assertThat(result.reservations()).allSatisfy(item -> {
            assertThat(item.statusHistory()).isEmpty();
            assertThat(item.merchantOwnerUsername()).isEqualTo("예약자 겸 점주");
        });
        verify(slots).findAllById(List.of(11L));
        verify(owners).findAllByPlaceIdIn(List.of(101L));
        verify(users).findAllById(Set.of(1L));
        verify(places).findAllById(List.of(101L));
    }

    @Test
    void emptyPageDoesNotQueryRelations() {
        when(reservations.findAllForAdmin(any(), any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());

        assertThat(service.listForAdmin(null, null, null, null, null, null, 1, 20).reservations()).isEmpty();

        verifyNoInteractions(slots, owners, users, places, histories);
    }

    @Test
    void missingAvailabilityKeepsReservationNotFoundError() {
        stubPage(List.of(reservation(21L, 1L, 11L)));

        assertThatThrownBy(() -> service.listForAdmin(null, null, null, null, null, null, 1, 20))
                .isInstanceOfSatisfying(ReservationException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND));

        verifyNoInteractions(owners, users, places, histories);
    }

    @Test
    void missingUsersAndPlaceKeepNullNamesButCurrentOwnerId() {
        stubPage(List.of(reservation(21L, 1L, 11L)));
        when(slots.findAllById(List.of(11L))).thenReturn(List.of(slot(11L, 101L)));
        when(owners.findAllByPlaceIdIn(List.of(101L))).thenReturn(List.of(owner(101L, 8L)));

        var result = service.listForAdmin(null, null, null, null, null, null, 1, 20).reservations().getFirst();

        assertThat(result.touristUserId()).isEqualTo(1L);
        assertThat(result.touristUsername()).isNull();
        assertThat(result.merchantOwnerUserId()).isEqualTo(8L);
        assertThat(result.merchantOwnerUsername()).isNull();
        assertThat(result.placeId()).isEqualTo(101L);
        assertThat(result.placeName()).isNull();
    }

    @Test
    void detailUsesSameBatchMappingAndKeepsMissingOwnerMappingNull() {
        Reservation reservation = reservation(21L, 1L, 11L);
        when(reservations.findById(21L)).thenReturn(Optional.of(reservation));
        stubPage(List.of(reservation));
        when(slots.findAllById(List.of(11L))).thenReturn(List.of(slot(11L, 101L)));
        when(users.findAllById(Set.of(1L))).thenReturn(List.of(user(1L, "예약자")));
        when(places.findAllById(List.of(101L))).thenReturn(List.of(place(101L, "장소")));

        var detail = service.getForAdmin(21L);
        var listItem = service.listForAdmin(null, null, null, null, null, null, 1, 20).reservations().getFirst();

        assertThat(detail).isEqualTo(listItem);
        assertThat(detail.merchantOwnerUserId()).isNull();
        assertThat(detail.merchantOwnerUsername()).isNull();
        assertThat(detail.touristUsername()).isEqualTo("예약자");
        assertThat(detail.statusHistory()).isEmpty();
    }

    private void stubPage(List<Reservation> content) {
        when(reservations.findAllForAdmin(any(), any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(content));
    }

    private Reservation reservation(Long id, Long touristId, Long slotId) {
        Reservation reservation = Reservation.create(touristId, slotId, "request-" + id, 1, NOW);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private PlaceAvailability slot(Long id, Long placeId) {
        PlaceAvailability slot = PlaceAvailability.create(7L, placeId, NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 10, NOW);
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private MerchantOwnerPlace owner(Long placeId, Long ownerId) {
        return MerchantOwnerPlace.builder().placeId(placeId).merchantOwnerUserId(ownerId).createdAt(NOW).build();
    }

    private User user(Long id, String name) {
        return User.builder().id(id).username(name).build();
    }

    private MapPlace place(Long id, String name) {
        return MapPlace.builder().id(id).name(name).build();
    }

    private ReservationStatusHistory history(Long reservationId, Long actorId, String reason) {
        return ReservationStatusHistory.of(reservationId, ReservationStatus.CONFIRMED, actorId, reason, NOW);
    }
}
