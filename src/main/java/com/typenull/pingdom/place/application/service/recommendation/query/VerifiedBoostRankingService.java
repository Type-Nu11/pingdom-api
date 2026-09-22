package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.boost.infrastructure.VerifiedBoostExecutionRepository;
import com.typenull.pingdom.place.support.VerifiedBoostRankingProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 현재 활성 조건을 만족하는 검증 프로모션 장소에 설정된 점수를 한 번 더함.
 * 반환된 장소 ID 집합은 응답의 boosted 표시에 사용하며 설정값 0이면 조회와 가점을 모두 생략.
 */
@Service
@RequiredArgsConstructor
class VerifiedBoostRankingService {

    private final VerifiedBoostExecutionRepository executionRepository;
    private final Clock clock;
    private final VerifiedBoostRankingProperties properties;

    RankingResult apply(List<ScoredCandidate> candidates) {
        if (candidates.isEmpty() || properties.score() <= 0d) {
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
                        ? candidate.withBoostScore(properties.score())
                        : candidate)
                .toList();
        return new RankingResult(ranked, Set.copyOf(boostedPlaceIds));
    }

    record RankingResult(List<ScoredCandidate> candidates, Set<Long> boostedPlaceIds) {
    }
}
