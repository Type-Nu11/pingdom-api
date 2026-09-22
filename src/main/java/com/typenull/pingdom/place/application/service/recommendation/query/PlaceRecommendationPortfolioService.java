package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureRecord;
import com.typenull.pingdom.place.application.service.recommendation.policy.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;

import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기본 점수 또는 실험군의 출처별 할당량으로 후보 포트폴리오를 만든 뒤 MMR로 유사 장소의 중복 노출을 완화합니다.
 * 출처별 후보는 장소 ID로 중복 제거하고 반올림한 할당량을 순서대로 채운 뒤 부족분을 기본 점수로 보충합니다.
 */
@Service
@RequiredArgsConstructor
class PlaceRecommendationPortfolioService {

    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    List<ScoredCandidate> buildCandidatePortfolio(
            int limit,
            PlaceRecommendationPolicyService.ResolvedRecommendationPolicy resolvedPolicy,
            List<ScoredCandidate> scoredCandidates
    ) {
        if (scoredCandidates.isEmpty()) {
            return List.of();
        }

        int targetSize = Math.min(
                scoredCandidates.size(),
                Math.max(limit, limit * resolvedPolicy.portfolioSizeMultiplier())
        );

        if (resolvedPolicy.stage() == RecommendationStage.STABLE) {
            return scoredCandidates.stream()
                    .sorted(baseScoreComparator().reversed())
                    .limit(targetSize)
                    .toList();
        }

        List<ScoredCandidate> portfolio = new ArrayList<>(targetSize);
        Set<Long> selectedPlaceIds = new HashSet<>();

        addPortfolioCandidates(
                portfolio,
                selectedPlaceIds,
                scoredCandidates.stream()
                        .filter(candidate -> candidate.personalScore() > 0d)
                        .sorted(Comparator
                                .comparingDouble(ScoredCandidate::personalScore)
                                .thenComparing(baseScoreComparator())
                                .reversed())
                        .toList(),
                quotaFor(targetSize, resolvedPolicy.mix().personalRatio()),
                targetSize,
                PlaceRecommendationCandidateSource.PERSONAL
        );
        addPortfolioCandidates(
                portfolio,
                selectedPlaceIds,
                scoredCandidates.stream()
                        .sorted(Comparator
                                .comparingDouble((ScoredCandidate candidate) ->
                                        candidate.engagementScore() + candidate.conversionScore() + candidate.qualityScore())
                                .thenComparing(baseScoreComparator())
                                .reversed())
                        .toList(),
                quotaFor(targetSize, resolvedPolicy.mix().popularRatio()),
                targetSize,
                PlaceRecommendationCandidateSource.POPULAR
        );
        addPortfolioCandidates(
                portfolio,
                selectedPlaceIds,
                scoredCandidates.stream()
                        .sorted(Comparator
                                .comparingDouble(ScoredCandidate::freshnessScore)
                                .thenComparingDouble(ScoredCandidate::explorationScore)
                                .thenComparing(baseScoreComparator())
                                .reversed())
                        .toList(),
                quotaFor(targetSize, resolvedPolicy.mix().freshRatio()),
                targetSize,
                PlaceRecommendationCandidateSource.FRESH
        );
        addPortfolioCandidates(
                portfolio,
                selectedPlaceIds,
                scoredCandidates.stream()
                        .sorted(Comparator
                                .comparingDouble(ScoredCandidate::geoScore)
                                .thenComparing(baseScoreComparator())
                                .reversed())
                        .toList(),
                quotaFor(targetSize, resolvedPolicy.mix().geoRatio()),
                targetSize,
                PlaceRecommendationCandidateSource.GEO
        );
        addPortfolioCandidates(
                portfolio,
                selectedPlaceIds,
                scoredCandidates.stream()
                        .sorted(baseScoreComparator().reversed())
                        .toList(),
                targetSize,
                targetSize,
                PlaceRecommendationCandidateSource.FALLBACK
        );

        return List.copyOf(portfolio);
    }

    List<ScoredCandidate> rerankWithMmr(
            List<ScoredCandidate> candidates,
            int limit,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            double mmrRelevanceWeight
    ) {
        List<ScoredCandidate> remaining = new ArrayList<>(candidates);
        List<ScoredCandidate> selected = new ArrayList<>();

        while (!remaining.isEmpty() && selected.size() < limit) {
            ScoredCandidate next = remaining.stream()
                    .max(Comparator
                            .comparingDouble((ScoredCandidate candidate) ->
                                    mmrScore(candidate, selected, similarityContext, mmrRelevanceWeight))
                            .thenComparing(baseScoreComparator()))
                    .orElseThrow();

            selected.add(next);
            remaining.remove(next);
        }

        return List.copyOf(selected);
    }

    List<PlaceRecommendationFeatureRecord> toFeatureRecords(List<ScoredCandidate> candidates) {
        List<PlaceRecommendationFeatureRecord> records = new ArrayList<>(candidates.size());
        int ranking = 1;
        for (ScoredCandidate candidate : candidates) {
            records.add(new PlaceRecommendationFeatureRecord(
                    candidate.place().getId(),
                    candidate.candidateSource(),
                    ranking++,
                    Math.round(candidate.distanceMeters()),
                    candidate.geoScore(),
                    candidate.personalScore(),
                    candidate.qualityScore(),
                    candidate.engagementScore(),
                    candidate.conversionScore(),
                    candidate.explorationScore(),
                    candidate.freshnessScore(),
                    candidate.trustScore(),
                    candidate.contextScore(),
                    candidate.benefitScore(),
                    candidate.availabilityScore(),
                    candidate.boostScore(),
                    candidate.finalScore()
            ));
        }
        return List.copyOf(records);
    }

    private void addPortfolioCandidates(
            List<ScoredCandidate> portfolio,
            Set<Long> selectedPlaceIds,
            List<ScoredCandidate> candidates,
            int quota,
            int targetSize,
            PlaceRecommendationCandidateSource candidateSource
    ) {
        if (quota <= 0 || portfolio.size() >= targetSize) {
            return;
        }

        int addedCount = 0;
        for (ScoredCandidate candidate : candidates) {
            if (addedCount >= quota || portfolio.size() >= targetSize) {
                return;
            }
            if (!selectedPlaceIds.add(candidate.place().getId())) {
                continue;
            }

            portfolio.add(candidate.withCandidateSource(candidateSource));
            addedCount++;
        }
    }

    private int quotaFor(int targetSize, double ratio) {
        if (ratio <= 0d) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(targetSize * ratio));
    }

    private Comparator<ScoredCandidate> baseScoreComparator() {
        return Comparator
                .comparingDouble(ScoredCandidate::finalScore)
                .thenComparingDouble(ScoredCandidate::personalScore)
                .thenComparingDouble(ScoredCandidate::engagementScore)
                .thenComparingDouble(ScoredCandidate::conversionScore)
                .thenComparingDouble(ScoredCandidate::qualityScore)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::distanceMeters).reversed())
                .thenComparing(candidate -> candidate.place().getId());
    }

    /**
     * 첫 후보는 최종 점수로 선택하고 이후에는 이미 선택한 장소와의 최대 유사도를 감점합니다.
     * 정책의 relevance 가중치가 1이면 유사도 감점을 없애고 원래 관련도 점수만 사용합니다.
     */
    private double mmrScore(
            ScoredCandidate candidate,
            List<ScoredCandidate> selected,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            double mmrRelevanceWeight
    ) {
        if (selected.isEmpty()) {
            return candidate.finalScore();
        }

        double maxSimilarityToSelected = selected.stream()
                .mapToDouble(selectedCandidate -> placeRecommendationSimilarityService.similarity(
                        candidate.place().getId(),
                        selectedCandidate.place().getId(),
                        similarityContext
                ))
                .max()
                .orElse(0d);

        return (mmrRelevanceWeight * candidate.finalScore())
                - ((1d - mmrRelevanceWeight) * maxSimilarityToSelected);
    }
}
