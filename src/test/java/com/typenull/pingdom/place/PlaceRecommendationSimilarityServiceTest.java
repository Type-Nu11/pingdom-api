package com.typenull.pingdom.place;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.application.service.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceSimilaritySnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceRecommendationSimilarityServiceTest {

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @Mock
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Mock
    private PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @BeforeEach
    void setUp() {
        placeRecommendationSimilarityService = new PlaceRecommendationSimilarityService(
                mapBookmarkRepository,
                mapImageLikeRepository,
                placeRecommendationSnapshotRepository,
                placeSimilaritySnapshotRepository
        );

        when(mapBookmarkRepository.findBookmarkUsersByPlaceIds(anyCollection())).thenReturn(List.of());
        when(mapImageLikeRepository.findLikeUsersByPlaceIds(anyCollection())).thenReturn(List.of());
        when(placeRecommendationSnapshotRepository.findByPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(placeSimilaritySnapshotRepository.findByPlaceIdsWithin(anyCollection())).thenReturn(List.of());
        when(mapBookmarkRepository.countDistinctUserId()).thenReturn(0L);
    }

    @Test
    void similarityReturnsSnapshotScoreWhenPairSnapshotExists() {
        MapPlace leftPlace = place(100L, 35.1801, 128.1078, 10L);
        MapPlace rightPlace = place(200L, 35.1802, 128.1079, 8L);

        when(placeSimilaritySnapshotRepository.findByPlaceIdsWithin(anyCollection())).thenReturn(List.of(
                PlaceSimilaritySnapshot.builder()
                        .leftPlaceId(100L)
                        .rightPlaceId(200L)
                        .geoKernelScore(0.11d)
                        .coBookmarkPmiScore(0.22d)
                        .coLikeCosineScore(0.33d)
                        .trendSimilarityScore(0.44d)
                        .totalSimilarityScore(0.55d)
                        .updatedAt(LocalDateTime.now())
                        .build()
        ));

        PlaceRecommendationSimilarityService.SimilarityContext context =
                placeRecommendationSimilarityService.buildContext(
                        List.of(100L, 200L),
                        Map.of(100L, leftPlace, 200L, rightPlace)
                );

        PlaceRecommendationSimilarityService.SimilarityScore score =
                placeRecommendationSimilarityService.score(100L, 200L, context);

        assertTrue(score.snapshotBacked());
        assertEquals(0.55d, score.totalSimilarity(), 0.000001d);
        assertEquals(0.22d, score.coBookmarkPmi(), 0.000001d);
    }

    @Test
    void similarityUsesGeoPmiCosineAndTrendWeightsWhenSnapshotIsMissing() {
        MapPlace leftPlace = place(100L, 35.1801, 128.1078, 12L);
        MapPlace rightPlace = place(200L, 35.1803, 128.1080, 9L);

        when(mapBookmarkRepository.findBookmarkUsersByPlaceIds(anyCollection())).thenReturn(List.of(
                projection(100L, 1L),
                projection(100L, 2L),
                projection(200L, 1L),
                projection(200L, 3L)
        ));
        when(mapImageLikeRepository.findLikeUsersByPlaceIds(anyCollection())).thenReturn(List.of(
                likeProjection(100L, 7L),
                likeProjection(100L, 8L),
                likeProjection(200L, 7L)
        ));
        when(mapBookmarkRepository.countDistinctUserId()).thenReturn(6L);
        PlaceRecommendationSimilarityService.SimilarityContext context =
                placeRecommendationSimilarityService.buildContext(
                        List.of(100L, 200L),
                        Map.of(100L, leftPlace, 200L, rightPlace),
                        false
                );

        PlaceRecommendationSimilarityService.SimilarityScore score =
                placeRecommendationSimilarityService.score(100L, 200L, context);

        assertTrue(score.geoKernel() > 0d);
        assertTrue(score.coBookmarkPmi() > 0d);
        assertTrue(score.coLikeCosine() > 0d);
        assertTrue(score.trendSimilarity() > 0d);

        double expected = (0.40d * score.geoKernel())
                + (0.35d * score.coBookmarkPmi())
                + (0.15d * score.coLikeCosine())
                + (0.10d * score.trendSimilarity());
        assertEquals(expected, score.totalSimilarity(), 0.000001d);
    }

    private MapPlace place(Long id, double latitude, double longitude, long photoCount) {
        return MapPlace.builder()
                .id(id)
                .name("place-" + id)
                .address("address-" + id)
                .latitude(latitude)
                .longitude(longitude)
                .photoCount(photoCount)
                .build();
    }

    private MapBookmarkRepository.PlaceBookmarkUserProjection projection(Long placeId, Long userId) {
        return new MapBookmarkRepository.PlaceBookmarkUserProjection() {
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }

    private MapImageLikeRepository.PlaceLikeUserProjection likeProjection(Long placeId, Long userId) {
        return new MapImageLikeRepository.PlaceLikeUserProjection() {
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }
}
