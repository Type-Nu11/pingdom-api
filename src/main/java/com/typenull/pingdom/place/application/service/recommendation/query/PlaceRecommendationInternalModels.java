package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

enum PersonalSignalType {
    BOOKMARK(3),
    LIKE(2),
    UPLOAD(1),
    NONE(0);

    private final int priority;

    PersonalSignalType(int priority) {
        this.priority = priority;
    }

    static PersonalSignalType stronger(PersonalSignalType left, PersonalSignalType right) {
        return left.priority >= right.priority ? left : right;
    }
}

record UserSignalContext(
        Map<Long, Double> seedWeights,
        Map<Long, PersonalSignalType> signalTypes,
        Set<Long> interactedPlaceIds
) {
    static UserSignalContext empty() {
        return new UserSignalContext(Map.of(), Map.of(), Set.of());
    }
}

enum CandidateSource {
    GEO,
    PERSONAL,
    TREND
}

/** 여러 원천에서 합친 장소와 현재 영업 상태. 일정이 없으면 영업 여부·확인 시각은 null. */
record CandidatePlace(
        MapPlace place,
        Set<CandidateSource> sources,
        Boolean currentlyOperating,
        LocalDateTime currentlyOperatingCheckedAt
) {
}

record PlaceDistance(
        MapPlace place,
        Set<CandidateSource> sources,
        double distanceMeters,
        Boolean currentlyOperating,
        LocalDateTime currentlyOperatingCheckedAt
) {
}

record CandidateSelection(
        java.util.List<PlaceDistance> candidates,
        double appliedRadiusKm,
        boolean fallbackCandidatePool
) {
}

final class CandidatePlaceAccumulator {
    private final MapPlace place;
    private final Set<CandidateSource> sources = new LinkedHashSet<>();

    CandidatePlaceAccumulator(MapPlace place, CandidateSource source) {
        this.place = place;
        this.sources.add(source);
    }

    MapPlace place() {
        return place;
    }

    Set<CandidateSource> sources() {
        return sources;
    }

    void addSource(CandidateSource source) {
        sources.add(source);
    }
}

/** 품질·참여·전환·탐색 점수를 후보 집합 안에서 정규화하기 전의 계산 결과. */
record IntermediateCandidate(
        MapPlace place,
        double distanceMeters,
        double geoScore,
        double personalScore,
        double rawQualityScore,
        double rawEngagementScore,
        double rawConversionScore,
        double rawExplorationScore,
        double freshnessScore,
        double trustScore,
        PersonalSignalType dominantSignalType
) {
}

record ScoredCandidate(
        MapPlace place,
        double distanceMeters,
        double geoScore,
        double personalScore,
        double qualityScore,
        double engagementScore,
        double conversionScore,
        double explorationScore,
        double freshnessScore,
        double trustScore,
        double contextScore,
        double benefitScore,
        double availabilityScore,
        double boostScore,
        PersonalSignalType dominantSignalType,
        double finalScore,
        PlaceRecommendationCandidateSource candidateSource
) {
    ScoredCandidate(
            MapPlace place,
            double distanceMeters,
            double geoScore,
            double personalScore,
            double qualityScore,
            double engagementScore,
            double conversionScore,
            double explorationScore,
            double freshnessScore,
            double trustScore,
            double contextScore,
            PersonalSignalType dominantSignalType,
            double finalScore,
            PlaceRecommendationCandidateSource candidateSource
    ) {
        this(
                place, distanceMeters, geoScore, personalScore, qualityScore, engagementScore,
                conversionScore, explorationScore, freshnessScore, trustScore, contextScore,
                0d, 0d, 0d, dominantSignalType, finalScore, candidateSource
        );
    }

    ScoredCandidate withCandidateSource(PlaceRecommendationCandidateSource candidateSource) {
        return new ScoredCandidate(
                place,
                distanceMeters,
                geoScore,
                personalScore,
                qualityScore,
                engagementScore,
                conversionScore,
                explorationScore,
                freshnessScore,
                trustScore,
                contextScore,
                benefitScore,
                availabilityScore,
                boostScore,
                dominantSignalType,
                finalScore,
                candidateSource
        );
    }

    ScoredCandidate withBoostScore(double boostScore) {
        return new ScoredCandidate(
                place,
                distanceMeters,
                geoScore,
                personalScore,
                qualityScore,
                engagementScore,
                conversionScore,
                explorationScore,
                freshnessScore,
                trustScore,
                contextScore,
                benefitScore,
                availabilityScore,
                boostScore,
                dominantSignalType,
                finalScore + boostScore,
                candidateSource
        );
    }
}

/** 원본 폴백과 저장 스냅샷을 구분하여 사진 수와 전환 수를 점수 계산에 전달. */
final class PlaceAggregate {
    long photoCount;
    long bookmarkCount;
    long likeSum;
    long clickCount;
    long bookmarkConversionCount;
    long likeConversionCount;
    long exposureCount;
    LocalDateTime latestCreatedAt;
    boolean snapshotBacked;

    static PlaceAggregate empty() {
        return new PlaceAggregate();
    }

    static PlaceAggregate fromSnapshot(PlaceRecommendationSnapshot snapshot) {
        PlaceAggregate aggregate = new PlaceAggregate();
        aggregate.photoCount = snapshot.getPhotoCount();
        aggregate.bookmarkCount = snapshot.getBookmarkCount();
        aggregate.likeSum = snapshot.getTotalLikeCount();
        aggregate.clickCount = snapshot.getClickCount();
        aggregate.bookmarkConversionCount = snapshot.getBookmarkConversionCount();
        aggregate.likeConversionCount = snapshot.getLikeConversionCount();
        aggregate.exposureCount = snapshot.getExposureCount();
        aggregate.latestCreatedAt = snapshot.getLatestPostCreatedAt();
        aggregate.snapshotBacked = true;
        return aggregate;
    }

    long resolvedPhotoCount(long fallbackPhotoCount) {
        return snapshotBacked ? photoCount : fallbackPhotoCount;
    }

    void mergeImageAggregate(Long likeSum, LocalDateTime latestCreatedAt) {
        this.likeSum = likeSum == null ? 0L : likeSum;
        this.latestCreatedAt = latestCreatedAt;
    }
}
