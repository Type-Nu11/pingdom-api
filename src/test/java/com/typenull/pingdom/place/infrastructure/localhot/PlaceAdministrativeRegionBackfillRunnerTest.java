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

    /** 지역 코드 누락 조회에 첫 페이지·설정 크기 2를 전달하고 반환된 장소 ID의 지역 동기화를 요청하는지 확인. */
    @Test
    void requestsConfiguredBackfillBatch() {
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

    /** 첫 장소 지역 조회가 도메인 오류를 내도 다음 장소의 동기화를 계속 호출하는지 확인. */
    @Test
    void continuesAfterRegionBackfillFailure() {
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

    /** 활성화된 backfill 설정과 전달된 배치 크기·서비스 대역을 묶어 시작 runner를 생성. */
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

    /** backfill이 읽는 장소 식별자만 고정한 mock을 생성. */
    private MapPlace place(long id) {
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(id);
        return place;
    }
}
