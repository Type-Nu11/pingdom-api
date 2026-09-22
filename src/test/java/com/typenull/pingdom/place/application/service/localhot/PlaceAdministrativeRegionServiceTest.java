package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceAdministrativeRegionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneOffset.UTC);

    /**
     * 좌표로 해석한 새 행정구역을 저장하고 장소에 같은 지역 코드를 반영하는지 확인합니다.
     */
    @Test
    void storesNewAdministrativeRegion() {
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

    /**
     * 기존 지역의 이름과 갱신 시각을 바꾸고 새 저장 호출 없이 장소에 지역 코드를 반영하는지 확인합니다.
     */
    @Test
    void refreshesExistingAdministrativeRegion() {
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

    /**
     * 비활성 resolver는 false를 반환하고 지역 저장소·장소에 접근하지 않는지 확인합니다.
     */
    @Test
    void skipsUnconfiguredRegionResolver() {
        PlaceAdministrativeRegionResolver resolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        MapPlace place = mock(MapPlace.class);
        when(resolver.isConfigured()).thenReturn(false);

        boolean synchronizedRegion = service(resolver, regionRepository, placeRepository).synchronizeIfConfigured(place);

        assertThat(synchronizedRegion).isFalse();
        verifyNoInteractions(regionRepository, placeRepository, place);
    }

    /**
     * 외부 해석 예외를 그대로 전파하면서 지역·장소 저장과 지역 코드 변경을 하지 않는지 확인합니다.
     */
    @Test
    void preservesStateOnResolutionFailure() {
        PlaceAdministrativeRegionResolver resolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        MapPlace place = mock(MapPlace.class);
        when(place.getLatitude()).thenReturn(37.5172d);
        when(place.getLongitude()).thenReturn(127.0473d);
        MapException resolutionFailure = new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED);
        when(resolver.resolve(37.5172d, 127.0473d)).thenThrow(resolutionFailure);

        assertThatThrownBy(() -> service(resolver, regionRepository, placeRepository).synchronize(place))
                .isSameAs(resolutionFailure);

        verifyNoInteractions(regionRepository, placeRepository);
        verify(place, org.mockito.Mockito.never()).updateAdministrativeRegion(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 잠금 조회한 장소가 없으면 ID를 포함한 예외를 반환하고 좌표 해석을 호출하지 않는지 확인합니다.
     */
    @Test
    void rejectsMissingRegionTarget() {
        PlaceAdministrativeRegionResolver resolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        when(resolver.isConfigured()).thenReturn(true);
        when(placeRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(resolver, regionRepository, placeRepository).synchronizeByIdIfConfigured(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeId=10");

        verifyNoInteractions(regionRepository);
        verify(resolver).isConfigured();
        verify(resolver, org.mockito.Mockito.never()).resolve(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    /**
     * 고정 UTC 시계를 사용하는 행정구역 동기화 서비스를 만듭니다.
     */
    private PlaceAdministrativeRegionService service(
            PlaceAdministrativeRegionResolver resolver,
            PlaceAdministrativeRegionRepository regionRepository,
            MapPlaceRepository placeRepository
    ) {
        return new PlaceAdministrativeRegionService(resolver, regionRepository, placeRepository, clock);
    }
}
