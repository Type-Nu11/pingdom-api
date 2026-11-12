package com.typenull.pingdom.place;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Mock
    private RecommendationMetrics recommendationMetrics;

    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    /**
     * 원본 반응과 유사도 스냅샷의 기본 조회를 빈 결과로 설정하여 각 사례가 필요한 신호만 추가하게 함.
     */
    @BeforeEach
    void setUp() {
        placeRecommendationSimilarityService = new PlaceRecommendationSimilarityService(
                mapBookmarkRepository,
                mapImageLikeRepository,
                placeRecommendationSnapshotRepository,
                placeSimilaritySnapshotRepository,
                recommendationMetrics
        );

        when(mapBookmarkRepository.findBookmarkUsersByPlaceIds(anyCollection())).thenReturn(List.of());
        when(mapImageLikeRepository.findLikeUsersByPlaceIds(anyCollection())).thenReturn(List.of());
        when(placeRecommendationSnapshotRepository.findByPlaceIdIn(anyCollection())).thenReturn(List.of());
        when(placeSimilaritySnapshotRepository.findByPlaceIdsWithin(anyCollection())).thenReturn(List.of());
        when(mapBookmarkRepository.countDistinctUserId()).thenReturn(0L);
    }

    @Test
    void aggregateInputsMatchUserSetScores() {
        MapPlace left = place(1L, 35.18, 128.10, 3L);
        MapPlace right = place(2L, 35.19, 128.11, 4L);
        var leftTrend = new PlaceRecommendationSimilarityService.TrendProfile(3, 0.5d, true);
        var rightTrend = new PlaceRecommendationSimilarityService.TrendProfile(4, 0.7d, true);
        var context = new PlaceRecommendationSimilarityService.SimilarityContext(
                Map.of(1L, left, 2L, right),
                Map.of(1L, Set.of(11L, 12L), 2L, Set.of(12L, 13L)),
                Map.of(1L, Set.of(21L, 22L), 2L, Set.of(22L)),
                Map.of(1L, leftTrend, 2L, rightTrend), Map.of(), 100L);
        var original = placeRecommendationSimilarityService.score(left, right, context);
        var aggregated = placeRecommendationSimilarityService.scoreFromCounts(left, right,
                2, 2, 1, 2, 1, 1, 100L, leftTrend, rightTrend);
        assertEquals(original, aggregated);
        var empty = placeRecommendationSimilarityService.scoreFromCounts(left, right,
                0, 0, 0, 0, 0, 0, 0, leftTrend, rightTrend);
        assertEquals(0d, empty.coBookmarkPmi());
        assertEquals(0d, empty.coLikeCosine());
    }

    /**
     * 저장된 쌍 스냅샷이 있으면 실시간 계산 대신 저장 점수와 snapshotBacked 표시가 반환되는지 확인.
     */
    @Test
    void prefersStoredSimilarity() {
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
                        .updatedAt(LocalDateTime.of(2010, 1, 1, 0, 0))
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
        verify(mapBookmarkRepository, never()).findBookmarkUsersByPlaceIds(anyCollection());
        verify(mapImageLikeRepository, never()).findLikeUsersByPlaceIds(anyCollection());
        verify(recommendationMetrics).recordSimilarityCoverage(true, 1L, 0L);
    }

    /**
     * 스냅샷이 없을 때 거리·공동 북마크·좋아요·추세가 양수 신호를 만들고 0.40/0.35/0.15/0.10 가중 합을 이루는지 확인.
     */
    @Test
    void combinesRealtimeSimilaritySignals() {
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
        assertEquals(1d / Math.sqrt(2d), score.coLikeCosine(), 0.000001d);
        verify(placeSimilaritySnapshotRepository, never()).findByPlaceIdsWithin(anyCollection());

        double expected = (0.40d * score.geoKernel())
                + (0.35d * score.coBookmarkPmi())
                + (0.15d * score.coLikeCosine())
                + (0.10d * score.trendSimilarity());
        assertEquals(expected, score.totalSimilarity(), 0.000001d);
    }

    @Test
    void loadsOnlyBothEndpointsOfMissingPairsAndPreservesRealtimeScore() {
        MapPlace first = place(100L, 35.1801, 128.1078, 12L);
        MapPlace second = place(200L, 35.1803, 128.1080, 9L);
        Map<Long, MapPlace> places = Map.of(
                100L, first,
                200L, second,
                300L, place(300L, 35.1805, 128.1082, 5L),
                400L, place(400L, 35.1807, 128.1084, 4L)
        );
        when(placeSimilaritySnapshotRepository.findByPlaceIdsWithin(anyCollection())).thenReturn(List.of(
                snapshot(100L, 300L), snapshot(300L, 100L), snapshot(100L, 400L),
                snapshot(200L, 300L), snapshot(200L, 400L), snapshot(300L, 400L)
        ));
        when(mapBookmarkRepository.findBookmarkUsersByPlaceIds(anyCollection())).thenReturn(List.of(
                projection(100L, 1L), projection(100L, 2L),
                projection(200L, 1L), projection(200L, 3L)
        ));
        when(mapImageLikeRepository.findLikeUsersByPlaceIds(anyCollection())).thenReturn(List.of(
                likeProjection(100L, 7L), likeProjection(100L, 7L), likeProjection(100L, 8L),
                likeProjection(200L, 7L)
        ));

        PlaceRecommendationSimilarityService.SimilarityContext partialContext =
                placeRecommendationSimilarityService.buildContext(
                        List.of(100L, 200L, 300L, 400L, 100L), places, true, 6L
                );
        PlaceRecommendationSimilarityService.SimilarityScore fallbackScore =
                placeRecommendationSimilarityService.score(100L, 200L, partialContext);
        PlaceRecommendationSimilarityService.SimilarityContext fullContext =
                placeRecommendationSimilarityService.buildContext(places.keySet(), places, false, 6L);

        assertEquals(placeRecommendationSimilarityService.score(100L, 200L, fullContext), fallbackScore);
        assertFalse(fallbackScore.snapshotBacked());
        assertTrue(placeRecommendationSimilarityService.score(400L, 100L, partialContext).snapshotBacked());
        verify(mapBookmarkRepository).findBookmarkUsersByPlaceIds(Set.of(100L, 200L));
        verify(mapImageLikeRepository).findLikeUsersByPlaceIds(Set.of(100L, 200L));
        verify(recommendationMetrics).recordSimilarityCoverage(true, 5L, 1L);
        verify(mapBookmarkRepository, never()).countDistinctUserId();
        InOrder queryOrder = inOrder(
                placeSimilaritySnapshotRepository, mapBookmarkRepository, mapImageLikeRepository
        );
        queryOrder.verify(placeSimilaritySnapshotRepository).findByPlaceIdsWithin(anyCollection());
        queryOrder.verify(mapBookmarkRepository).findBookmarkUsersByPlaceIds(Set.of(100L, 200L));
        queryOrder.verify(mapImageLikeRepository).findLikeUsersByPlaceIds(Set.of(100L, 200L));
    }

    @Test
    void loadsAllReactionsWhenEverySnapshotIsMissing() {
        Set<Long> placeIds = Set.of(100L, 200L, 300L);

        placeRecommendationSimilarityService.buildContext(placeIds, Map.of(), true, 10L);

        verify(mapBookmarkRepository).findBookmarkUsersByPlaceIds(placeIds);
        verify(mapImageLikeRepository).findLikeUsersByPlaceIds(placeIds);
        verify(recommendationMetrics).recordSimilarityCoverage(true, 0L, 3L);
    }

    @Test
    void skipsQueriesForEmptyPlaceIds() {
        PlaceRecommendationSimilarityService.SimilarityContext context =
                placeRecommendationSimilarityService.buildContext(List.of(), Map.of(), true, 10L);

        assertTrue(context.snapshotScoresByPair().isEmpty());
        assertEquals(0L, context.totalBookmarkUserCount());
        verifyNoInteractions(mapBookmarkRepository, mapImageLikeRepository,
                placeRecommendationSnapshotRepository, placeSimilaritySnapshotRepository, recommendationMetrics);
    }

    @Test
    void singleDistinctPlaceUsesIdentityWithoutReactionQueries() {
        MapPlace onlyPlace = place(100L, 35.1801, 128.1078, 12L);

        PlaceRecommendationSimilarityService.SimilarityContext context =
                placeRecommendationSimilarityService.buildContext(
                        List.of(100L, 100L), Map.of(100L, onlyPlace), true, 10L
                );

        assertEquals(1d, placeRecommendationSimilarityService.similarity(100L, 100L, context));
        assertEquals(10L, context.totalBookmarkUserCount());
        verify(mapBookmarkRepository, never()).findBookmarkUsersByPlaceIds(anyCollection());
        verify(mapImageLikeRepository, never()).findLikeUsersByPlaceIds(anyCollection());
        verify(recommendationMetrics).recordSimilarityCoverage(true, 0L, 0L);
    }

    private PlaceSimilaritySnapshot snapshot(Long leftPlaceId, Long rightPlaceId) {
        return PlaceSimilaritySnapshot.builder()
                .leftPlaceId(leftPlaceId)
                .rightPlaceId(rightPlaceId)
                .geoKernelScore(0.11d)
                .coBookmarkPmiScore(0.22d)
                .coLikeCosineScore(0.33d)
                .trendSimilarityScore(0.44d)
                .totalSimilarityScore(0.55d)
                .updatedAt(LocalDateTime.of(2010, 1, 1, 0, 0))
                .build();
    }

    /**
     * 거리와 콘텐츠 신호를 조절할 수 있는 장소 fixture를 생성.
     */
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

    /**
     * 북마크 사용자와 장소의 조회 projection을 구성.
     */
    private MapBookmarkRepository.PlaceBookmarkUserProjection projection(Long placeId, Long userId) {
        return new MapBookmarkRepository.PlaceBookmarkUserProjection() {
            /** 사용자 간 공동 반응을 비교할 장소 ID를 반환. */
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            /** 북마크 또는 좋아요 사용자 집합에 포함할 ID를 반환. */
            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }

    /**
     * 좋아요 사용자와 장소의 조회 projection을 구성.
     */
    private MapImageLikeRepository.PlaceLikeUserProjection likeProjection(Long placeId, Long userId) {
        return new MapImageLikeRepository.PlaceLikeUserProjection() {
            /** 사용자 간 공동 반응을 비교할 장소 ID를 반환. */
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            /** 북마크 또는 좋아요 사용자 집합에 포함할 ID를 반환. */
            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }
}
