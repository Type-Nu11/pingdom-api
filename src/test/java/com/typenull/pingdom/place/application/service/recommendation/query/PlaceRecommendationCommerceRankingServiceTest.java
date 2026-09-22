package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceRecommendationCommerceRankingServiceTest {

    private final PlaceRecommendationCommerceRankingService service =
            new PlaceRecommendationCommerceRankingService();

    /**
     * 혜택 0.05와 예약 0.07을 독립적으로 더해 기본 점수 0.40이 0.52가 되는지 확인합니다.
     */
    @Test
    void addsIndependentCommerceBoosts() {
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(1L);
        ScoredCandidate candidate = candidate(place, 0.40d);

        ScoredCandidate boosted = service.apply(
                List.of(candidate),
                Map.of(1L, new PlaceRecommendationCommerceSignalLoader.CommerceSignal(true, true)),
                0.05d,
                0.07d
        ).getFirst();

        assertThat(boosted.benefitScore()).isEqualTo(0.05d);
        assertThat(boosted.availabilityScore()).isEqualTo(0.07d);
        assertThat(boosted.finalScore()).isEqualTo(0.52d);
    }

    /**
     * 상거래 신호가 없으면 각 가점은 0이고 기존 최종 점수가 유지되는지 확인합니다.
     */
    @Test
    void preservesScoreWithoutCommerce() {
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(1L);
        ScoredCandidate candidate = candidate(place, 0.40d);

        ScoredCandidate unchanged = service.apply(
                List.of(candidate),
                Map.of(),
                0.05d,
                0.07d
        ).getFirst();

        assertThat(unchanged.benefitScore()).isZero();
        assertThat(unchanged.availabilityScore()).isZero();
        assertThat(unchanged.finalScore()).isEqualTo(0.40d);
    }

    /**
     * 지정 최종 점수를 가진 상거래 가점 전 후보를 만듭니다.
     */
    private ScoredCandidate candidate(MapPlace place, double finalScore) {
        return new ScoredCandidate(
                place,
                100d,
                0.2d,
                0.2d,
                0.2d,
                0.2d,
                0.2d,
                0.2d,
                0.2d,
                0.2d,
                0d,
                PersonalSignalType.NONE,
                finalScore,
                PlaceRecommendationCandidateSource.FALLBACK
        );
    }
}
