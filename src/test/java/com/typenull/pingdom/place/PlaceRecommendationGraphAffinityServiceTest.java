package com.typenull.pingdom.place;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceRecommendationGraphAffinityServiceTest {

    @Mock
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    private PlaceRecommendationGraphAffinityService placeRecommendationGraphAffinityService;

    @BeforeEach
    void setUp() {
        placeRecommendationGraphAffinityService =
                new PlaceRecommendationGraphAffinityService(placeRecommendationSimilarityService);
    }

    @Test
    void scorePropagatesAffinityAcrossIntermediatePlace() {
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

    @Test
    void scoreReturnsZeroWhenSeedDoesNotExist() {
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
