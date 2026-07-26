package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.boost.infrastructure.VerifiedBoostExecutionRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.support.VerifiedBoostRankingProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerifiedBoostRankingServiceTest {

    @Test
    void eligibleActiveExecutionAddsConfiguredScore() {
        VerifiedBoostExecutionRepository repository = mock(VerifiedBoostExecutionRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        VerifiedBoostRankingService service = new VerifiedBoostRankingService(
                repository, clock, new VerifiedBoostRankingProperties(0.08d));
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(1L);
        when(repository.findEligibleActivePlaceIds(List.of(1L),
                LocalDateTime.of(2026, 7, 26, 12, 0))).thenReturn(List.of(1L));

        var result = service.apply(List.of(candidate(place, 0.40d)));

        assertThat(result.candidates().getFirst().finalScore()).isCloseTo(0.48d,
                org.assertj.core.data.Offset.offset(0.000_001d));
        assertThat(result.boostedPlaceIds()).containsExactly(1L);
        assertThat(result.candidates().getFirst().boostScore()).isEqualTo(0.08d);
    }

    @Test
    void ineligibleCandidateKeepsScoreUnchanged() {
        VerifiedBoostExecutionRepository repository = mock(VerifiedBoostExecutionRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        VerifiedBoostRankingService service = new VerifiedBoostRankingService(
                repository, clock, new VerifiedBoostRankingProperties(0.08d));
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(1L);
        when(repository.findEligibleActivePlaceIds(List.of(1L),
                LocalDateTime.of(2026, 7, 26, 12, 0))).thenReturn(List.of());

        var result = service.apply(List.of(candidate(place, 0.40d)));

        assertThat(result.candidates().getFirst().finalScore()).isEqualTo(0.40d);
        assertThat(result.boostedPlaceIds()).isEmpty();
    }

    private ScoredCandidate candidate(MapPlace place, double finalScore) {
        return new ScoredCandidate(
                place, 100d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d,
                0d, PersonalSignalType.NONE, finalScore, PlaceRecommendationCandidateSource.FALLBACK);
    }
}
