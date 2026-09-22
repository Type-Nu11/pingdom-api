package com.typenull.pingdom.place;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceRecommendationGraphAffinityServiceTest {

    @Mock
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    private PlaceRecommendationGraphAffinityService placeRecommendationGraphAffinityService;

    /**
     * 모의 유사도 서비스를 주입하여 그래프 전파 규칙만 독립적으로 검사.
     */
    @BeforeEach
    void setUp() {
        placeRecommendationGraphAffinityService =
                new PlaceRecommendationGraphAffinityService(placeRecommendationSimilarityService);
    }

    /**
     * 직접 시드와 유사한 중간 장소를 통해 후속 후보에도 양수 친화도가 전파되고 약한 직접 연결보다 높아지는지 확인.
     */
    @Test
    void propagatesIndirectAffinity() {
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                new PlaceRecommendationSimilarityService.SimilarityContext(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        0L
                );

        stubBidirectionalSimilarity(similarityContext, 100L, 200L, 0.80d);
        stubBidirectionalSimilarity(similarityContext, 200L, 300L, 0.85d);
        stubBidirectionalSimilarity(similarityContext, 100L, 300L, 0.05d);
        stubBidirectionalSimilarity(similarityContext, 100L, 400L, 0.12d);

        Map<Long, Double> scores = placeRecommendationGraphAffinityService.score(
                List.of(200L, 300L, 400L),
                Map.of(100L, 1.0d),
                similarityContext
        );

        assertTrue(scores.get(200L) > scores.get(300L));
        assertTrue(scores.get(300L) > scores.get(400L));
        assertTrue(scores.get(300L) > 0d);
    }

    /**
     * 개인 시드가 없으면 모든 후보의 친화도가 0인지 확인.
     */
    @Test
    void returnsZeroWithoutSeeds() {
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                new PlaceRecommendationSimilarityService.SimilarityContext(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        0L
                );

        Map<Long, Double> scores = placeRecommendationGraphAffinityService.score(
                List.of(200L, 300L),
                Map.of(),
                similarityContext
        );

        assertEquals(0d, scores.get(200L));
        assertEquals(0d, scores.get(300L));
    }

    /**
     * 시드와 후보 각 200개를 주어도 유사도 호출이 64개 노드의 쌍 수인 2,016회를 넘지 않는지 확인.
     */
    @Test
    void boundsGraphPairCalculations() {
        PlaceRecommendationSimilarityService.SimilarityContext context =
                new PlaceRecommendationSimilarityService.SimilarityContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0L);
        List<Long> candidates = IntStream.rangeClosed(1, 200).mapToObj(Long::valueOf).toList();
        Map<Long, Double> seeds = IntStream.rangeClosed(1, 200)
                .boxed().collect(java.util.stream.Collectors.toMap(Long::valueOf, ignored -> 1d));

        when(placeRecommendationSimilarityService.similarity(anyLong(), anyLong(), same(context))).thenReturn(0d);
        placeRecommendationGraphAffinityService.score(candidates, seeds, context);

        verify(placeRecommendationSimilarityService, atMost(2016))
                .similarity(anyLong(), anyLong(), same(context));
    }

    /**
     * 장소 쌍을 어느 방향으로 조회해도 같은 유사도 값을 반환하도록 준비.
     */
    private void stubBidirectionalSimilarity(
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            Long leftPlaceId,
            Long rightPlaceId,
            double similarity
    ) {
        when(placeRecommendationSimilarityService.similarity(leftPlaceId, rightPlaceId, similarityContext))
                .thenReturn(similarity);
        when(placeRecommendationSimilarityService.similarity(rightPlaceId, leftPlaceId, similarityContext))
                .thenReturn(similarity);
    }
}
