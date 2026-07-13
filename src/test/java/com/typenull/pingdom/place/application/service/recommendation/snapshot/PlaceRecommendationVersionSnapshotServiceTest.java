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

    @Test
    void increaseExposureCountsUsesSnapshotCreatedAfterPlaceLock() {
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
