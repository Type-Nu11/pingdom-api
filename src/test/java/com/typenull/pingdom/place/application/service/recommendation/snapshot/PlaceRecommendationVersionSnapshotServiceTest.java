package com.typenull.pingdom.place.application.service.recommendation.snapshot;


import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationVersionSnapshotServiceTest {

    private static final String RECOMMENDATION_VERSION = "place-rec-v1";

    @Mock
    private PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Mock
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Mock
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    private PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    /**
     * 버전별 스냅샷과 노출·클릭·전환 저장소 모의 객체로 재집계 서비스를 구성한다.
     */
    @BeforeEach
    void setUp() {
        placeRecommendationVersionSnapshotService = new PlaceRecommendationVersionSnapshotService(
                placeRecommendationVersionSnapshotRepository,
                mapPlaceRepository,
                placeRecommendationExposureRepository,
                placeRecommendationClickRepository,
                placeRecommendationConversionRepository
        );
    }

    /**
     * 최초 조회는 비어 있지만 장소 잠금 조회 후 노출 3건 스냅샷이 나타나면 기존 값을 4건으로 증가시켜 저장하는지 확인한다.
     */
    @Test
    void incrementsSnapshotFoundAfterLock() {
        PlaceRecommendationVersionSnapshot snapshot = PlaceRecommendationVersionSnapshot.builder()
                .id(10L)
                .placeId(1L)
                .recommendationVersion(RECOMMENDATION_VERSION)
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(3L)
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                any(),
                eq(RECOMMENDATION_VERSION)
        )).thenReturn(List.of());
        when(placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersionForReadLock(
                any(),
                eq(RECOMMENDATION_VERSION)
        )).thenReturn(List.of(snapshot));
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createPlace(1L)));

        placeRecommendationVersionSnapshotService.increaseExposureCounts(List.of(1L), RECOMMENDATION_VERSION);

        assertThat(snapshot.getExposureCount()).isEqualTo(4L);
        verify(placeRecommendationVersionSnapshotRepository).saveAll(List.of(snapshot));
    }

    /**
     * 대상 장소의 기존 버전에 원본 집계가 없으면 해당 스냅샷을 삭제하고 새 스냅샷은 저장하지 않는지 확인한다.
     */
    @Test
    void deletesVersionsWithoutMetrics() {
        PlaceRecommendationVersionSnapshot staleSnapshot = PlaceRecommendationVersionSnapshot.builder()
                .id(10L)
                .placeId(1L)
                .recommendationVersion(RECOMMENDATION_VERSION)
                .clickCount(1L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(3L)
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createPlace(1L)));
        when(placeRecommendationVersionSnapshotRepository.findByPlaceIdIn(List.of(1L)))
                .thenReturn(List.of(staleSnapshot));

        PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult result =
                placeRecommendationVersionSnapshotService.resyncPlace(1L);

        assertThat(result.synchronizedSnapshotCount()).isZero();
        assertThat(result.deletedSnapshotCount()).isEqualTo(1L);
        verify(placeRecommendationVersionSnapshotRepository).deleteAllByIdInBatch(List.of(10L));
        verify(placeRecommendationVersionSnapshotRepository, never()).saveAll(any());
    }

    /**
     * 기존 스냅샷 없이 버전별 노출 집계가 있으면 동기화 1건·삭제 0건을 반환하고 스냅샷 저장을 호출하는지 확인한다.
     */
    @Test
    void createsVersionFromExposureAggregate() {
        PlaceRecommendationExposureRepository.PlaceVersionExposureCountProjection projection =
                mock(PlaceRecommendationExposureRepository.PlaceVersionExposureCountProjection.class);
        when(projection.getPlaceId()).thenReturn(1L);
        when(projection.getRecommendationVersion()).thenReturn(RECOMMENDATION_VERSION);
        when(projection.getExposureCount()).thenReturn(7L);
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createPlace(1L)));
        when(placeRecommendationExposureRepository
                .countExposuresByPlaceIdsGroupedByPlaceIdAndRecommendationVersion(List.of(1L)))
                .thenReturn(List.of(projection));
        when(placeRecommendationVersionSnapshotRepository.findByPlaceIdIn(List.of(1L)))
                .thenReturn(List.of());

        PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult result =
                placeRecommendationVersionSnapshotService.resyncPlace(1L);

        assertThat(result.synchronizedSnapshotCount()).isEqualTo(1L);
        assertThat(result.deletedSnapshotCount()).isZero();
        verify(placeRecommendationVersionSnapshotRepository).saveAll(any());
    }

    /**
     * 스냅샷 최초 생성 전 장소 잠금 조회가 반환할 장소를 지정 ID로 구성한다.
     */
    private MapPlace createPlace(Long placeId) {
        return MapPlace.builder()
                .id(placeId)
                .name("place")
                .address("address")
                .latitude(35.1801d)
                .longitude(128.1078d)
                .userId(1L)
                .registrant("tester")
                .photoCount(1L)
                .build();
    }
}
