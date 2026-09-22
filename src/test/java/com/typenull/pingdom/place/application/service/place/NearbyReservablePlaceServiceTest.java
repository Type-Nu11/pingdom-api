package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.place.api.dto.place.reservable.NearbyReservablePlaceResponse;
import com.typenull.pingdom.place.infrastructure.persistence.place.NearbyReservablePlaceQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.NearbyReservablePlaceQueryRepository.NearbyReservablePlaceProjection;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NearbyReservablePlaceServiceTest {

    @Mock
    private NearbyReservablePlaceQueryRepository repository;

    @Mock
    private NearbyReservablePlaceProjection projection;

    @Mock
    private Clock clock;

    @InjectMocks
    private PlaceQueryServiceImpl service;

    /**
     * 반경 km를 m로 변환하고 인원·상품·정렬·기준 시각을 저장소에 전달하며 거리 반올림과 상품명을 응답에 반영하는지 확인합니다.
     */
    @Test
    void mapsNearbyReservablePlace() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 0);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-02T11:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(projection.getPlaceId()).thenReturn(101L);
        when(projection.getName()).thenReturn("이월드");
        when(projection.getDistanceMeters()).thenReturn(429.6d);
        when(projection.getAvailabilityId()).thenReturn(77L);
        when(projection.getAvailableStartsAt()).thenReturn(now.plusDays(1));
        when(projection.getAvailableEndsAt()).thenReturn(now.plusDays(1).plusHours(1));
        when(projection.getRemainingCapacity()).thenReturn(42);
        when(projection.getProductType()).thenReturn(AvailabilityProductType.TICKET);
        when(projection.getProductId()).thenReturn(12L);
        when(projection.getProductName()).thenReturn("이월드 오후 입장권");
        when(repository.findNearbyReservablePlaces(
                eq(35.8714d), eq(128.6014d), eq(3_000d), eq(null), eq(null), eq(2), eq("TICKET"),
                eq(null), eq(null), eq("NEAREST"), eq(now), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(projection)));

        NearbyReservablePlaceResponse response = service.listNearbyReservablePlaces(new NearbyReservablePlaceCondition(
                1, 20, 35.8714d, 128.6014d, 3.0d, null, null, 2,
                AvailabilityProductType.TICKET, null, null, "NEAREST"));

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst().distanceMeters()).isEqualTo(430L);
        assertThat(response.places().getFirst().productName()).isEqualTo("이월드 오후 입장권");
        verify(repository).findNearbyReservablePlaces(
                eq(35.8714d), eq(128.6014d), eq(3_000d), eq(null), eq(null), eq(2), eq("TICKET"),
                eq(null), eq(null), eq("NEAREST"), eq(now), any(Pageable.class));
    }

    /**
     * 0번 페이지와 과도한 페이지 크기·역전된 기간을 가진 요청이 MapException으로 거절되는지 확인합니다.
     */
    @Test
    void rejectsInvalidPagingAndDateRange() {
        assertThatThrownBy(() -> service.listNearbyReservablePlaces(new NearbyReservablePlaceCondition(
                0, 20, 35.8714d, 128.6014d, 3.0d, null, null, 1,
                null, null, null, "NEAREST")))
                .isInstanceOf(MapException.class);

        assertThatThrownBy(() -> service.listNearbyReservablePlaces(new NearbyReservablePlaceCondition(
                1, 101, 35.8714d, 128.6014d, 3.0d,
                LocalDateTime.of(2026, 9, 3, 12, 0), LocalDateTime.of(2026, 9, 3, 11, 0), 1,
                null, null, null, "NEAREST")))
                .isInstanceOf(MapException.class);
    }
}
