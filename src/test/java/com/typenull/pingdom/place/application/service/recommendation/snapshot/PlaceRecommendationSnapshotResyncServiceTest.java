package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceSimilaritySnapshotResyncService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationSnapshotResyncServiceTest {

    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private MapBookmarkRepository mapBookmarkRepository;
    @Mock private MapImageRepository mapImageRepository;
    @Mock private PlaceRecommendationClickRepository placeRecommendationClickRepository;
    @Mock private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    @Mock private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    @Mock private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    @Mock private PlaceSimilaritySnapshotResyncService placeSimilaritySnapshotResyncService;
    @Mock private PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    @InjectMocks
    private PlaceRecommendationSnapshotResyncService resyncService;

    /**
     * 단일 장소의 원본 집계와 유사도·버전 단건 결과를 합치고 전체 장소 순회를 호출하지 않는지 확인합니다.
     */
    @Test
    void resyncsOnlyRequestedPlace() {
        MapPlace place = createPlace(17L);
        when(mapPlaceRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(place));
        when(placeSimilaritySnapshotResyncService.resyncPlace(place))
                .thenReturn(new PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult(2L, 1L));
        when(placeRecommendationVersionSnapshotService.resyncPlace(17L))
                .thenReturn(new PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult(3L, 1L));

        PlaceRecommendationSnapshotResyncService.SnapshotResyncResult result = resyncService.resyncPlace(17L);

        assertThat(result.placeCount()).isEqualTo(1L);
        assertThat(result.synchronizedSnapshotCount()).isEqualTo(1L);
        assertThat(result.synchronizedSimilaritySnapshotCount()).isEqualTo(2L);
        assertThat(result.deletedSimilaritySnapshotCount()).isEqualTo(1L);
        assertThat(result.synchronizedVersionSnapshotCount()).isEqualTo(3L);
        assertThat(result.deletedVersionSnapshotCount()).isEqualTo(1L);
        verify(mapBookmarkRepository).findBookmarkCountsByPlaceIds(List.of(17L));
        verify(mapImageRepository).findPlaceAggregatesByPlaceIds(List.of(17L));
        verify(placeRecommendationClickRepository).countClicksByPlaceIds(List.of(17L));
        verify(placeRecommendationConversionRepository).countConversionsByPlaceIds(List.of(17L));
        verify(placeRecommendationExposureRepository).countExposuresByPlaceIds(List.of(17L));
        verify(placeRecommendationSnapshotRepository).saveAll(any());
        verify(mapPlaceRepository, never()).count();
        verify(mapPlaceRepository, never()).findAll(any(Pageable.class));
    }

    /**
     * 대상 장소가 없으면 전체·유사도·버전 스냅샷 정리를 호출하고 삭제 건수를 반환하는지 확인합니다.
     */
    @Test
    void cleansDeletedPlaceSnapshots() {
        when(mapPlaceRepository.findByIdForUpdate(17L)).thenReturn(Optional.empty());
        when(placeRecommendationSnapshotRepository.existsById(17L)).thenReturn(true);
        when(placeSimilaritySnapshotResyncService.deleteForPlace(17L)).thenReturn(2L);
        when(placeRecommendationVersionSnapshotService.resyncPlace(17L))
                .thenReturn(new PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult(0L, 1L));

        PlaceRecommendationSnapshotResyncService.SnapshotResyncResult result = resyncService.resyncPlace(17L);

        assertThat(result.placeCount()).isZero();
        assertThat(result.deletedSnapshotCount()).isEqualTo(1L);
        assertThat(result.deletedSimilaritySnapshotCount()).isEqualTo(2L);
        assertThat(result.deletedVersionSnapshotCount()).isEqualTo(1L);
        verify(placeRecommendationSnapshotRepository).deleteById(17L);
        verify(placeSimilaritySnapshotResyncService).deleteForPlace(17L);
        verify(placeRecommendationVersionSnapshotService).resyncPlace(17L);
    }

    /**
     * 단일 재동기화 대상 장소를 고정된 콘텐츠 수로 만듭니다.
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
