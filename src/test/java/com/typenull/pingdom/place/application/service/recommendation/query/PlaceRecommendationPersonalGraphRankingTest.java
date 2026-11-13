package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceRecommendationPersonalGraphRankingTest {

    @Test
    void appliesStablePersonalWeightToConnectedCandidateWithSixtyFourSeeds() {
        var similarityService = mock(PlaceRecommendationSimilarityService.class);
        var context = new PlaceRecommendationSimilarityService.SimilarityContext(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0L
        );
        when(similarityService.similarity(1L, 1_000L, context)).thenReturn(0.8d);
        Map<Long, Double> seeds = new LinkedHashMap<>();
        for (long id = 1; id <= 64; id++) {
            seeds.put(id, 1d);
        }
        Map<Long, Double> affinities = new PlaceRecommendationGraphAffinityService(similarityService)
                .score(List.of(1_000L, 1_001L), seeds, context);
        List<IntermediateCandidate> candidates = List.of(
                candidate(1_000L, affinities.get(1_000L)),
                candidate(1_001L, affinities.get(1_001L))
        );
        var scoringService = new PlaceRecommendationScoringService(similarityService);

        List<ScoredCandidate> personalized = scoringService.applyFinalScores(
                candidates, new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d, 0d)
        );
        List<ScoredCandidate> anonymous = scoringService.applyFinalScores(
                candidates, new RankingWeights(0.48d, 0d, 0.16d, 0.10d, 0.08d, 0.12d, 0.09d, 0d)
        );

        assertThat(personalized.get(0).finalScore()).isEqualTo(0.30d);
        assertThat(personalized.get(1).finalScore()).isZero();
        assertThat(anonymous).extracting(ScoredCandidate::finalScore).containsExactly(0d, 0d);
    }

    private IntermediateCandidate candidate(Long placeId, double personalScore) {
        return new IntermediateCandidate(
                MapPlace.builder().id(placeId).name("place-" + placeId).build(),
                100d, 0d, personalScore, 0d, 0d, 0d, 0d, 0d, 0d, PersonalSignalType.NONE
        );
    }
}
