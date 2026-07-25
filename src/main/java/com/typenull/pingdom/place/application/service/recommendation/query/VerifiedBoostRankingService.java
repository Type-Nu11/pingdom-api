package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.boost.infrastructure.VerifiedBoostExecutionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class VerifiedBoostRankingService {

    private final VerifiedBoostExecutionRepository executionRepository;
    private final Clock clock;

    @Value("${place.recommendation.verified-boost-score:0.08}")
    private double boostScore;

    RankingResult apply(List<ScoredCandidate> candidates) {
        if (candidates.isEmpty() || boostScore <= 0d) {
            return new RankingResult(candidates, Set.of());
        }
        List<Long> candidatePlaceIds = candidates.stream().map(candidate -> candidate.place().getId()).toList();
        Set<Long> boostedPlaceIds = new HashSet<>(executionRepository.findEligibleActivePlaceIds(
                candidatePlaceIds, LocalDateTime.now(clock)));
        if (boostedPlaceIds.isEmpty()) {
            return new RankingResult(candidates, Set.of());
        }
        List<ScoredCandidate> ranked = candidates.stream()
                .map(candidate -> boostedPlaceIds.contains(candidate.place().getId())
                        ? candidate.withFinalScore(candidate.finalScore() + boostScore)
                        : candidate)
                .toList();
        return new RankingResult(ranked, Set.copyOf(boostedPlaceIds));
    }

    record RankingResult(List<ScoredCandidate> candidates, Set<Long> boostedPlaceIds) {
    }
}
