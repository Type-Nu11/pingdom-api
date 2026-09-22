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

    /**
     * 활성 프로모션 조회에 포함된 장소에 설정 가점 0.08을 더하고 boosted ID와 점수 기여를 반환하는지 확인.
     */
    @Test
    void addsEligibleBoostScore() {
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

    /**
     * 활성 프로모션에 없는 후보는 점수를 유지하고 boosted 목록에서 제외하는지 확인.
     */
    @Test
    void preservesIneligibleBoostScore() {
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

    /**
     * 프로모션 적용 전 기본 점수를 지정한 후보를 생성.
     */
    private ScoredCandidate candidate(MapPlace place, double finalScore) {
        return new ScoredCandidate(
                place, 100d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d, 0.2d,
                0d, PersonalSignalType.NONE, finalScore, PlaceRecommendationCandidateSource.FALLBACK);
    }
}
