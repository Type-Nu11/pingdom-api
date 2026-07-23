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

    @Test
    void activeBenefitAndAvailabilityBoostAreAddedIndependently() {
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

    @Test
    void missingCommerceSignalKeepsScoreUnchanged() {
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
