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

    /**
     * 신뢰 조회에 없는 장소는 맵에서 제외되어 호출자의 기본값 0.5를 적용할 수 있는지 확인합니다.
     */
    @Test
    void defaultsMissingTrustToNeutral() {
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

    /**
     * 신뢰 가중치만 1이면 후보 최종 점수가 각각의 신뢰도 0.2와 0.9가 되는지 확인합니다.
     */
    @Test
    void usesExclusiveTrustWeight() {
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

    /**
     * 신뢰 가중치를 포함한 가중치 합이 1이 아니면 생성 시 명확한 예외로 거절하는지 확인합니다.
     */
    @Test
    void rejectsInvalidTrustWeightSum() {
        assertThatThrownBy(() -> new RankingWeights(0.3d, 0.3d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d, 0.1d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    /**
     * 신뢰도 비교용 최소 장소를 만듭니다.
     */
    private MapPlace place(Long id) {
        return MapPlace.builder().id(id).name("place-" + id).build();
    }

    /**
     * 같은 거리와 영업 상태를 가진 조회 후보를 만듭니다.
     */
    private PlaceDistance distance(MapPlace place) {
        return new PlaceDistance(place, java.util.Set.of(), 100d, true, null);
    }

    /**
     * 신뢰도만 다르고 다른 신호는 0인 정규화 전 후보를 만듭니다.
     */
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
