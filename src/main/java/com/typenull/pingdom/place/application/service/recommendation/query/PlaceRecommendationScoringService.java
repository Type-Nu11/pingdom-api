package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PlaceRecommendationScoringService {

    private static final double FRESHNESS_DECAY_DAYS = 14d;
    private static final double BAYESIAN_PRIOR_WEIGHT = 3d;
    private static final double CTR_PRIOR_WEIGHT = 8d;
    private static final double CTR_CONFIDENCE_SAMPLE_SIZE = 10d;
    private static final double CONVERSION_PRIOR_WEIGHT = 10d;
    private static final double CONVERSION_CONFIDENCE_SAMPLE_SIZE = 12d;
    private static final double LIKE_CONVERSION_WEIGHT = 0.60d;
    private static final Clock RECOMMENDATION_CLOCK = Clock.systemUTC();

    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    RecommendationScoreContext buildScoreContext(
            List<PlaceDistance> candidates,
            Map<Long, PlaceAggregate> aggregateMap,
            long totalClickCount,
            long totalExposureCount
    ) {
        return new RecommendationScoreContext(
                calculateGlobalCtr(totalClickCount, totalExposureCount),
                calculateGlobalConversionRate(candidates, aggregateMap),
                calculateGlobalAverageLikePerPhoto(candidates, aggregateMap),
                Instant.now(RECOMMENDATION_CLOCK)
        );
    }

    List<IntermediateCandidate> buildIntermediateCandidates(
            List<PlaceDistance> candidates,
            Map<Long, PlaceAggregate> aggregateMap,
            UserSignalContext signalContext,
            Map<Long, Double> graphAffinityScores,
            long totalExposureCount,
            double maxSeedWeight,
            double appliedRadiusKm,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            RecommendationScoreContext scoreContext
    ) {
        return candidates.stream()
                .map(candidate -> toIntermediateCandidate(
                        candidate,
                        aggregateMap.getOrDefault(candidate.place().getId(), PlaceAggregate.empty()),
                        signalContext,
                        graphAffinityScores.getOrDefault(candidate.place().getId(), 0d),
                        scoreContext.globalCtr(),
                        scoreContext.globalConversionRate(),
                        totalExposureCount,
                        scoreContext.globalAverageLikePerPhoto(),
                        maxSeedWeight,
                        appliedRadiusKm,
                        similarityContext,
                        scoreContext.recommendationBaseTime()
                ))
                .toList();
    }

    List<ScoredCandidate> applyFinalScores(
            List<IntermediateCandidate> candidates,
            RankingWeights weights
    ) {
        double minQuality = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawQualityScore)
                .min()
                .orElse(0d);
        double maxQuality = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawQualityScore)
                .max()
                .orElse(0d);
        double minExploration = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawExplorationScore)
                .min()
                .orElse(0d);
        double maxExploration = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawExplorationScore)
                .max()
                .orElse(0d);
        double minEngagement = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawEngagementScore)
                .min()
                .orElse(0d);
        double maxEngagement = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawEngagementScore)
                .max()
                .orElse(0d);
        double minConversion = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawConversionScore)
                .min()
                .orElse(0d);
        double maxConversion = candidates.stream()
                .mapToDouble(IntermediateCandidate::rawConversionScore)
                .max()
                .orElse(0d);

        return candidates.stream()
                .map(candidate -> {
                    double normalizedQuality = normalize(candidate.rawQualityScore(), minQuality, maxQuality);
                    double normalizedEngagement = normalize(candidate.rawEngagementScore(), minEngagement, maxEngagement);
                    double normalizedConversion = normalize(candidate.rawConversionScore(), minConversion, maxConversion);
                    double normalizedExploration = normalize(
                            candidate.rawExplorationScore(),
                            minExploration,
                            maxExploration
                    );
                    double finalScore = (weights.geoWeight() * candidate.geoScore())
                            + (weights.personalWeight() * candidate.personalScore())
                            + (weights.qualityWeight() * normalizedQuality)
                            + (weights.engagementWeight() * normalizedEngagement)
                            + (weights.conversionWeight() * normalizedConversion)
                            + (weights.freshnessWeight() * candidate.freshnessScore())
                            + (weights.explorationWeight() * normalizedExploration);

                    return new ScoredCandidate(
                            candidate.place(),
                            candidate.distanceMeters(),
                            candidate.geoScore(),
                            candidate.personalScore(),
                            normalizedQuality,
                            normalizedEngagement,
                            normalizedConversion,
                            normalizedExploration,
                            candidate.freshnessScore(),
                            candidate.dominantSignalType(),
                            finalScore,
                            PlaceRecommendationCandidateSource.FALLBACK
                    );
                })
                .toList();
    }

    private double calculateGlobalAverageLikePerPhoto(
            List<PlaceDistance> candidates,
            Map<Long, PlaceAggregate> aggregateMap
    ) {
        return candidates.stream()
                .map(candidate -> {
                    PlaceAggregate aggregate = aggregateMap.getOrDefault(candidate.place().getId(), PlaceAggregate.empty());
                    long photoCount = aggregate.resolvedPhotoCount(candidate.place().currentPhotoCount());
                    if (photoCount <= 0L) {
                        return 0d;
                    }
                    return (double) aggregate.likeSum / (double) photoCount;
                })
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
    }

    private double calculateGlobalConversionRate(
            List<PlaceDistance> candidates,
            Map<Long, PlaceAggregate> aggregateMap
    ) {
        double weightedConversionSum = 0d;
        long exposureSum = 0L;

        for (PlaceDistance candidate : candidates) {
            PlaceAggregate aggregate = aggregateMap.getOrDefault(candidate.place().getId(), PlaceAggregate.empty());
            if (aggregate.exposureCount <= 0L) {
                continue;
            }

            weightedConversionSum += aggregate.bookmarkConversionCount
                    + (aggregate.likeConversionCount * LIKE_CONVERSION_WEIGHT);
            exposureSum += aggregate.exposureCount;
        }

        if (exposureSum <= 0L || weightedConversionSum <= 0d) {
            return 0d;
        }
        return weightedConversionSum / (double) exposureSum;
    }

    private IntermediateCandidate toIntermediateCandidate(
            PlaceDistance candidate,
            PlaceAggregate aggregate,
            UserSignalContext signalContext,
            double graphAffinityScore,
            double globalCtr,
            double globalConversionRate,
            long totalExposureCount,
            double globalAverageLikePerPhoto,
            double maxSeedWeight,
            double appliedRadiusKm,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext,
            Instant recommendationBaseTime
    ) {
        double personalScore = graphAffinityScore;
        PersonalSignalType dominantSignalType = resolveDominantSignalType(
                candidate.place().getId(),
                signalContext,
                maxSeedWeight,
                similarityContext
        );

        double geoScore = 1d - Math.min(candidate.distanceMeters() / 1_000d / appliedRadiusKm, 1d);
        double rawExplorationScore = calculateExplorationScore(totalExposureCount, aggregate.exposureCount);
        double rawEngagementScore = calculateEngagementScore(aggregate.clickCount, aggregate.exposureCount, globalCtr);
        double rawConversionScore = calculateConversionScore(
                aggregate.bookmarkConversionCount,
                aggregate.likeConversionCount,
                aggregate.exposureCount,
                globalConversionRate
        );

        long photoCount = aggregate.resolvedPhotoCount(candidate.place().currentPhotoCount());
        double smoothedLikeAverage = (aggregate.likeSum + BAYESIAN_PRIOR_WEIGHT * globalAverageLikePerPhoto)
                / (photoCount + BAYESIAN_PRIOR_WEIGHT);
        double rawQualityScore = smoothedLikeAverage
                + (Math.log1p(aggregate.bookmarkCount) * 0.35d)
                + (Math.log1p(photoCount) * 0.20d);
        double freshnessScore = calculateFreshnessScore(aggregate.latestCreatedAt, recommendationBaseTime);

        return new IntermediateCandidate(
                candidate.place(),
                candidate.distanceMeters(),
                geoScore,
                personalScore,
                rawQualityScore,
                rawEngagementScore,
                rawConversionScore,
                rawExplorationScore,
                freshnessScore,
                dominantSignalType
        );
    }

    private PersonalSignalType resolveDominantSignalType(
            Long candidatePlaceId,
            UserSignalContext signalContext,
            double maxSeedWeight,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        double maxContribution = 0d;
        PersonalSignalType dominantSignalType = PersonalSignalType.NONE;

        for (Map.Entry<Long, Double> seedWeightEntry : signalContext.seedWeights().entrySet()) {
            double similarity = placeRecommendationSimilarityService.similarity(
                    candidatePlaceId,
                    seedWeightEntry.getKey(),
                    similarityContext
            );
            double normalizedSeedWeight = maxSeedWeight > 0d ? seedWeightEntry.getValue() / maxSeedWeight : 0d;
            double contribution = normalizedSeedWeight * similarity;

            if (contribution > maxContribution) {
                maxContribution = contribution;
                dominantSignalType = signalContext.signalTypes().getOrDefault(
                        seedWeightEntry.getKey(),
                        PersonalSignalType.NONE
                );
            }
        }

        if (dominantSignalType != PersonalSignalType.NONE) {
            return dominantSignalType;
        }

        return signalContext.seedWeights().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> signalContext.signalTypes().getOrDefault(entry.getKey(), PersonalSignalType.NONE))
                .orElse(PersonalSignalType.NONE);
    }

    private double calculateFreshnessScore(LocalDateTime latestCreatedAt, Instant recommendationBaseTime) {
        if (latestCreatedAt == null) {
            return 0d;
        }

        Instant latestCreatedAtInstant = latestCreatedAt.atOffset(ZoneOffset.UTC).toInstant();
        double days = Math.max(0d, Duration.between(latestCreatedAtInstant, recommendationBaseTime).toHours() / 24d);
        return Math.exp(-days / FRESHNESS_DECAY_DAYS);
    }

    private double calculateExplorationScore(long totalExposureCount, long placeExposureCount) {
        return Math.sqrt(Math.log(totalExposureCount + 1d) / (placeExposureCount + 1d));
    }

    private double calculateGlobalCtr(long totalClickCount, long totalExposureCount) {
        if (totalExposureCount <= 0L || totalClickCount <= 0L) {
            return 0d;
        }
        return (double) totalClickCount / (double) totalExposureCount;
    }

    private double calculateEngagementScore(long clickCount, long exposureCount, double globalCtr) {
        if (exposureCount <= 0L) {
            return 0d;
        }

        double smoothedCtr = (clickCount + (CTR_PRIOR_WEIGHT * globalCtr))
                / (exposureCount + CTR_PRIOR_WEIGHT);
        double confidence = Math.min(exposureCount / CTR_CONFIDENCE_SAMPLE_SIZE, 1d);
        return smoothedCtr * confidence;
    }

    private double calculateConversionScore(
            long bookmarkConversionCount,
            long likeConversionCount,
            long exposureCount,
            double globalConversionRate
    ) {
        if (exposureCount <= 0L) {
            return 0d;
        }

        double weightedConversionCount = bookmarkConversionCount + (likeConversionCount * LIKE_CONVERSION_WEIGHT);
        double smoothedConversionRate = (weightedConversionCount + (CONVERSION_PRIOR_WEIGHT * globalConversionRate))
                / (exposureCount + CONVERSION_PRIOR_WEIGHT);
        double confidence = Math.min(exposureCount / CONVERSION_CONFIDENCE_SAMPLE_SIZE, 1d);
        return smoothedConversionRate * confidence;
    }

    private double normalize(double value, double min, double max) {
        if (Double.compare(min, max) == 0) {
            return max > 0d ? 0.5d : 0d;
        }
        return (value - min) / (max - min);
    }

    record RecommendationScoreContext(
            double globalCtr,
            double globalConversionRate,
            double globalAverageLikePerPhoto,
            Instant recommendationBaseTime
    ) {
    }
}
