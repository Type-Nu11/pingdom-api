package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationSnapshotServiceTest {

    @Mock
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageRepository mapImageRepository;

    private PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    @BeforeEach
    void setUp() {
        placeRecommendationSnapshotService = new PlaceRecommendationSnapshotService(
                placeRecommendationSnapshotRepository,
                mapPlaceRepository,
                mapBookmarkRepository,
                mapImageRepository
        );
    }

    @Test
    void increaseExposureCountsUsesSnapshotCreatedAfterPlaceLock() {
        PlaceRecommendationSnapshot snapshot = PlaceRecommendationSnapshot.builder()
                .placeId(1L)
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(3L)
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(placeRecommendationSnapshotRepository.findByPlaceIdIn(any())).thenReturn(List.of());
        when(placeRecommendationSnapshotRepository.findByPlaceIdInForReadLock(any())).thenReturn(List.of(snapshot));
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(createPlace(1L)));

        placeRecommendationSnapshotService.increaseExposureCounts(List.of(1L));

        assertThat(snapshot.getExposureCount()).isEqualTo(4L);
        verify(placeRecommendationSnapshotRepository).saveAll(List.of(snapshot));
        verify(mapBookmarkRepository, never()).countByPlaceId(1L);
        verify(mapImageRepository, never()).sumLikeCountByPlaceId(1L);
    }

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
