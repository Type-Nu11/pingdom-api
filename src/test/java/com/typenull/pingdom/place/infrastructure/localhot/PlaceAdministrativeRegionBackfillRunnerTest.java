package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.localhot.PlaceAdministrativeRegionService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.domain.Pageable;

class PlaceAdministrativeRegionBackfillRunnerTest {

    @Test
    void regionCode가_없는_장소만_설정된_batchSize만큼_조회한다() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceAdministrativeRegionService regionService = mock(PlaceAdministrativeRegionService.class);
        MapPlace place = place(10L);
        when(placeRepository.findByRegionCodeIsNullOrderByIdAsc(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(place));
        when(regionService.synchronizeByIdIfConfigured(10L)).thenReturn(false);
        PlaceAdministrativeRegionBackfillRunner runner = runner(2, placeRepository, regionService);

        runner.run(mock(ApplicationArguments.class));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(placeRepository).findByRegionCodeIsNullOrderByIdAsc(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
        verify(regionService).synchronizeByIdIfConfigured(10L);
    }

    @Test
    void 한_장소의_지역_조회가_실패해도_다음_장소를_계속_처리한다() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceAdministrativeRegionService regionService = mock(PlaceAdministrativeRegionService.class);
        MapPlace failedPlace = place(10L);
        MapPlace succeededPlace = place(11L);
        when(placeRepository.findByRegionCodeIsNullOrderByIdAsc(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(failedPlace, succeededPlace));
        doThrow(new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND))
                .when(regionService).synchronizeByIdIfConfigured(10L);
        when(regionService.synchronizeByIdIfConfigured(11L)).thenReturn(true);

        runner(10, placeRepository, regionService).run(mock(ApplicationArguments.class));

        verify(regionService).synchronizeByIdIfConfigured(10L);
        verify(regionService).synchronizeByIdIfConfigured(11L);
    }

    private PlaceAdministrativeRegionBackfillRunner runner(
            int batchSize,
            MapPlaceRepository placeRepository,
            PlaceAdministrativeRegionService regionService
    ) {
        return new PlaceAdministrativeRegionBackfillRunner(
                new PlaceAdministrativeRegionBackfillProperties(true, batchSize),
                placeRepository,
                regionService
        );
    }

    private MapPlace place(long id) {
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(id);
        return place;
    }
}
