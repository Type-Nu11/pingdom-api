package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceAdministrativeRegionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void 신규_행정구역을_저장하고_장소에_regionCode를_반영한다() {
        PlaceAdministrativeRegionResolver resolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        MapPlace place = mock(MapPlace.class);
        ResolvedPlaceAdministrativeRegion resolved = new ResolvedPlaceAdministrativeRegion(
                "11680", "서울특별시", "강남구", "서울특별시 강남구"
        );
        when(place.getLatitude()).thenReturn(37.5172d);
        when(place.getLongitude()).thenReturn(127.0473d);
        when(resolver.resolve(37.5172d, 127.0473d)).thenReturn(resolved);
        when(regionRepository.findById("11680")).thenReturn(Optional.empty());

        service(resolver, regionRepository, placeRepository).synchronize(place);

        ArgumentCaptor<PlaceAdministrativeRegion> regionCaptor = ArgumentCaptor.forClass(PlaceAdministrativeRegion.class);
        verify(regionRepository).save(regionCaptor.capture());
        assertThat(regionCaptor.getValue()).satisfies(region -> {
            assertThat(region.getCode()).isEqualTo("11680");
            assertThat(region.getRegionName()).isEqualTo("서울특별시 강남구");
        });
        verify(place).updateAdministrativeRegion("11680");
    }

    @Test
    void 기존_행정구역의_이름과_갱신시각을_갱신하고_장소에_regionCode를_반영한다() {
        PlaceAdministrativeRegionResolver resolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        MapPlace place = mock(MapPlace.class);
        PlaceAdministrativeRegion existing = PlaceAdministrativeRegion.from(
                new ResolvedPlaceAdministrativeRegion("11680", "서울특별시", "강남구", "기존 이름"),
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0)
        );
        when(place.getLatitude()).thenReturn(37.5172d);
        when(place.getLongitude()).thenReturn(127.0473d);
        when(resolver.resolve(37.5172d, 127.0473d)).thenReturn(
                new ResolvedPlaceAdministrativeRegion("11680", "서울특별시", "강남구", "서울특별시 강남구")
        );
        when(regionRepository.findById("11680")).thenReturn(Optional.of(existing));

        service(resolver, regionRepository, placeRepository).synchronize(place);

        assertThat(existing.getRegionName()).isEqualTo("서울특별시 강남구");
        assertThat(existing.getUpdatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 17, 1, 0));
        verify(regionRepository, org.mockito.Mockito.never()).save(any());
        verify(place).updateAdministrativeRegion("11680");
    }

    private PlaceAdministrativeRegionService service(
            PlaceAdministrativeRegionResolver resolver,
            PlaceAdministrativeRegionRepository regionRepository,
            MapPlaceRepository placeRepository
    ) {
        return new PlaceAdministrativeRegionService(resolver, regionRepository, placeRepository, clock);
    }
}
