package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrustScoreRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceRecommendationTrustRankingTest {

    @Test
    void loader는_조회되지_않은_장소를_중립_점수로_해석할_수_있도록_결과에서_제외한다() {
        PlaceRecommendationTrustScoreRepository repository = mock(PlaceRecommendationTrustScoreRepository.class);
        PlaceRecommendationTrustScoreRepository.PlaceTrustScoreProjection projection =
                mock(PlaceRecommendationTrustScoreRepository.PlaceTrustScoreProjection.class);
        MapPlace trustedPlace = place(1L);
        MapPlace unknownPlace = place(2L);

        when(projection.getPlaceId()).thenReturn(1L);
        when(projection.getTrustScore()).thenReturn(0.9d);
        when(repository.findTrustScoresByPlaceIds(List.of(1L, 2L))).thenReturn(List.of(projection));

        Map<Long, Double> result = new PlaceRecommendationTrustScoreLoader(repository).load(List.of(
                distance(trustedPlace),
                distance(unknownPlace)
        ));

        assertThat(result).containsEntry(1L, 0.9d).doesNotContainKey(2L);
        assertThat(result.getOrDefault(2L, PlaceRecommendationTrustScoreLoader.NEUTRAL_TRUST_SCORE))
                .isEqualTo(0.5d);
    }

    @Test
    void trustWeight만_활성화하면_신뢰도가_높은_장소의_최종_점수가_높다() {
        PlaceRecommendationScoringService service = new PlaceRecommendationScoringService(mock(
                com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.class
        ));
        IntermediateCandidate lowTrust = candidate(place(1L), 0.2d);
        IntermediateCandidate highTrust = candidate(place(2L), 0.9d);

        List<ScoredCandidate> result = service.applyFinalScores(
                List.of(lowTrust, highTrust),
                new RankingWeights(0d, 0d, 0d, 0d, 0d, 0d, 0d, 1d)
        );

        assertThat(result).extracting(ScoredCandidate::finalScore).containsExactly(0.2d, 0.9d);
    }

    @Test
    void trustWeight가_활성화된_가중치의_합은_1이어야_한다() {
        assertThatThrownBy(() -> new RankingWeights(0.3d, 0.3d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    private MapPlace place(Long id) {
        return MapPlace.builder().id(id).name("place-" + id).build();
    }

    private PlaceDistance distance(MapPlace place) {
        return new PlaceDistance(place, java.util.Set.of(), 100d, true, null);
    }

    private IntermediateCandidate candidate(MapPlace place, double trustScore) {
        return new IntermediateCandidate(
                place,
                100d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                trustScore,
                PersonalSignalType.NONE
        );
    }
}
