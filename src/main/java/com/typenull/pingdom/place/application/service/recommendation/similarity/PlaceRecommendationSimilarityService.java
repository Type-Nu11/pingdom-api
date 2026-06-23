package com.typenull.pingdom.place.application.service.recommendation.similarity;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationSimilarityService {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final double GEO_SIMILARITY_DECAY_METERS = 2_500d;
    private static final double TREND_FRESHNESS_DECAY_DAYS = 14d;
    private static final Duration BOOKMARK_USER_COUNT_CACHE_TTL = Duration.ofHours(1);
    private static final Clock SIMILARITY_CLOCK = Clock.systemUTC();

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    private final Object bookmarkUserCountLock = new Object();
    private volatile long cachedTotalBookmarkUserCount;
    private volatile Instant cachedBookmarkUserCountLoadedAt;

    public SimilarityContext buildContext(Collection<Long> placeIds, Map<Long, MapPlace> placeIndex) {
        return buildContext(placeIds, placeIndex, true, null);
    }

    public SimilarityContext buildContext(
            Collection<Long> placeIds,
            Map<Long, MapPlace> placeIndex,
            boolean loadSimilaritySnapshots
    ) {
        return buildContext(placeIds, placeIndex, loadSimilaritySnapshots, null);
    }

    public SimilarityContext buildContext(
            Collection<Long> placeIds,
            Map<Long, MapPlace> placeIndex,
            boolean loadSimilaritySnapshots,
            Long totalBookmarkUserCount
    ) {
        Map<Long, Set<Long>> bookmarkUsersByPlace = new HashMap<>();
        Map<Long, Set<Long>> likeUsersByPlace = new HashMap<>();
        Map<Long, TrendProfile> trendProfilesByPlace = new HashMap<>();
        Map<PlacePairKey, SimilarityScore> snapshotScoresByPair = new HashMap<>();

        if (!placeIds.isEmpty()) {
            for (MapBookmarkRepository.PlaceBookmarkUserProjection projection :
                    mapBookmarkRepository.findBookmarkUsersByPlaceIds(placeIds)) {
                bookmarkUsersByPlace
                        .computeIfAbsent(projection.getPlaceId(), ignored -> new HashSet<>())
                        .add(projection.getUserId());
            }

            for (MapImageLikeRepository.PlaceLikeUserProjection projection :
                    mapImageLikeRepository.findLikeUsersByPlaceIds(placeIds)) {
                likeUsersByPlace
                        .computeIfAbsent(projection.getPlaceId(), ignored -> new HashSet<>())
                        .add(projection.getUserId());
            }

            for (PlaceRecommendationSnapshot snapshot : placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
                trendProfilesByPlace.put(
                        snapshot.getPlaceId(),
                        TrendProfile.of(snapshot.getPhotoCount(), snapshot.getLatestPostCreatedAt())
                );
            }

            if (loadSimilaritySnapshots) {
                for (PlaceSimilaritySnapshot snapshot : placeSimilaritySnapshotRepository.findByPlaceIdsWithin(placeIds)) {
                    snapshotScoresByPair.put(
                            PlacePairKey.of(snapshot.getLeftPlaceId(), snapshot.getRightPlaceId()),
                            SimilarityScore.snapshot(
                                    snapshot.getGeoKernelScore(),
                                    snapshot.getCoBookmarkPmiScore(),
                                    snapshot.getCoLikeCosineScore(),
                                    snapshot.getTrendSimilarityScore(),
                                    snapshot.getTotalSimilarityScore()
                            )
                    );
                }
            }
        }

        for (Map.Entry<Long, MapPlace> placeEntry : placeIndex.entrySet()) {
            trendProfilesByPlace.putIfAbsent(
                    placeEntry.getKey(),
                    TrendProfile.of(placeEntry.getValue().currentPhotoCount(), null)
            );
        }

        return new SimilarityContext(
                placeIndex,
                bookmarkUsersByPlace,
                likeUsersByPlace,
                trendProfilesByPlace,
                snapshotScoresByPair,
                resolveTotalBookmarkUserCount(placeIds, totalBookmarkUserCount)
        );
    }

    private long resolveTotalBookmarkUserCount(Collection<Long> placeIds, Long precomputedTotalBookmarkUserCount) {
        if (placeIds.isEmpty()) {
            return 0L;
        }
        if (precomputedTotalBookmarkUserCount != null) {
            return precomputedTotalBookmarkUserCount;
        }
        return cachedTotalBookmarkUserCount();
    }

    public long cachedTotalBookmarkUserCount() {
        Instant now = SIMILARITY_CLOCK.instant();
        Instant loadedAt = cachedBookmarkUserCountLoadedAt;
        if (loadedAt != null && !loadedAt.plus(BOOKMARK_USER_COUNT_CACHE_TTL).isBefore(now)) {
            return cachedTotalBookmarkUserCount;
        }

        synchronized (bookmarkUserCountLock) {
            loadedAt = cachedBookmarkUserCountLoadedAt;
            if (loadedAt != null && !loadedAt.plus(BOOKMARK_USER_COUNT_CACHE_TTL).isBefore(now)) {
                return cachedTotalBookmarkUserCount;
            }

            long refreshedCount = mapBookmarkRepository.countDistinctUserId();
            cachedTotalBookmarkUserCount = refreshedCount;
            cachedBookmarkUserCountLoadedAt = now;
            return refreshedCount;
        }
    }

    public double similarity(Long leftPlaceId, Long rightPlaceId, SimilarityContext context) {
        return score(leftPlaceId, rightPlaceId, context).totalSimilarity();
    }

    public SimilarityScore score(Long leftPlaceId, Long rightPlaceId, SimilarityContext context) {
        return score(
                leftPlaceId,
                rightPlaceId,
                context.placeIndex().get(leftPlaceId),
                context.placeIndex().get(rightPlaceId),
                context
        );
    }

    public SimilarityScore score(MapPlace leftPlace, MapPlace rightPlace, SimilarityContext context) {
        if (leftPlace == null || rightPlace == null) {
            return SimilarityScore.empty();
        }
        return score(leftPlace.getId(), rightPlace.getId(), leftPlace, rightPlace, context);
    }

    private SimilarityScore score(
            Long leftPlaceId,
            Long rightPlaceId,
            MapPlace leftPlace,
            MapPlace rightPlace,
            SimilarityContext context
    ) {
        if (Objects.equals(leftPlaceId, rightPlaceId)) {
            return SimilarityScore.identity();
        }

        SimilarityScore snapshotScore = context.snapshotScoresByPair()
                .get(PlacePairKey.of(leftPlaceId, rightPlaceId));
        if (snapshotScore != null) {
            return snapshotScore;
        }

        if (leftPlace == null || rightPlace == null) {
            return SimilarityScore.empty();
        }

        double geoKernel = geoKernel(leftPlace, rightPlace);
        double coBookmarkPmi = normalizedPmi(
                context.bookmarkUsersByPlace().get(leftPlaceId),
                context.bookmarkUsersByPlace().get(rightPlaceId),
                context.totalBookmarkUserCount()
        );
        double coLikeCosine = cosine(
                context.likeUsersByPlace().get(leftPlaceId),
                context.likeUsersByPlace().get(rightPlaceId)
        );
        double trendSimilarity = trendSimilarity(
                context.trendProfilesByPlace().getOrDefault(leftPlaceId, TrendProfile.fallback(leftPlace)),
                context.trendProfilesByPlace().getOrDefault(rightPlaceId, TrendProfile.fallback(rightPlace))
        );
        double totalSimilarity = (0.40d * geoKernel)
                + (0.35d * coBookmarkPmi)
                + (0.15d * coLikeCosine)
                + (0.10d * trendSimilarity);

        return SimilarityScore.realtime(
                geoKernel,
                coBookmarkPmi,
                coLikeCosine,
                trendSimilarity,
                totalSimilarity
        );
    }

    private double geoKernel(MapPlace leftPlace, MapPlace rightPlace) {
        if (leftPlace.getLatitude() == null || leftPlace.getLongitude() == null
                || rightPlace.getLatitude() == null || rightPlace.getLongitude() == null) {
            return 0d;
        }

        double distanceMeters = calculateDistanceMeters(
                leftPlace.getLatitude(),
                leftPlace.getLongitude(),
                rightPlace.getLatitude(),
                rightPlace.getLongitude()
        );
        return Math.exp(-distanceMeters / GEO_SIMILARITY_DECAY_METERS);
    }

    private double normalizedPmi(Set<Long> left, Set<Long> right, long totalUserCount) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty() || totalUserCount <= 0L) {
            return 0d;
        }

        long intersectionSize = intersectionSize(left, right);
        if (intersectionSize <= 0L) {
            return 0d;
        }

        double rawPmi = Math.log(
                ((double) intersectionSize * (double) totalUserCount)
                        / ((double) left.size() * (double) right.size())
        );
        if (rawPmi <= 0d) {
            return 0d;
        }

        return rawPmi / (1d + rawPmi);
    }

    private double cosine(Set<Long> left, Set<Long> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return 0d;
        }

        long intersectionSize = intersectionSize(left, right);
        if (intersectionSize <= 0L) {
            return 0d;
        }

        return intersectionSize / Math.sqrt((double) left.size() * (double) right.size());
    }

    private long intersectionSize(Set<Long> left, Set<Long> right) {
        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = smaller == left ? right : left;

        long intersectionSize = 0L;
        for (Long value : smaller) {
            if (larger.contains(value)) {
                intersectionSize++;
            }
        }
        return intersectionSize;
    }

    private double trendSimilarity(TrendProfile left, TrendProfile right) {
        if (left == null || right == null || !left.hasSignal() || !right.hasSignal()) {
            return 0d;
        }

        // 현재는 시계열 성장 로그가 없어서 사진 누적량과 최신 업로드 신선도를 성장 패턴 대용 지표로 사용한다.
        double volumeSimilarity = 1d / (1d + Math.abs(Math.log1p(left.photoCount()) - Math.log1p(right.photoCount())));
        double freshnessSimilarity = 1d - Math.abs(left.freshnessScore() - right.freshnessScore());

        return (0.60d * volumeSimilarity) + (0.40d * freshnessSimilarity);
    }

    private double calculateDistanceMeters(
            double baseLatitude,
            double baseLongitude,
            double targetLatitude,
            double targetLongitude
    ) {
        double latitudeDelta = Math.toRadians(targetLatitude - baseLatitude);
        double longitudeDelta = Math.toRadians(targetLongitude - baseLongitude);
        double baseLatitudeRadians = Math.toRadians(baseLatitude);
        double targetLatitudeRadians = Math.toRadians(targetLatitude);

        double a = Math.sin(latitudeDelta / 2d) * Math.sin(latitudeDelta / 2d)
                + Math.cos(baseLatitudeRadians) * Math.cos(targetLatitudeRadians)
                * Math.sin(longitudeDelta / 2d) * Math.sin(longitudeDelta / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static double calculateFreshnessScore(LocalDateTime latestPostCreatedAt) {
        if (latestPostCreatedAt == null) {
            return 0d;
        }

        double days = Math.max(
                0d,
                Duration.between(
                        latestPostCreatedAt.atOffset(ZoneOffset.UTC).toInstant(),
                        SIMILARITY_CLOCK.instant()
                ).toHours() / 24d
        );
        return Math.exp(-days / TREND_FRESHNESS_DECAY_DAYS);
    }

    public record SimilarityContext(
            Map<Long, MapPlace> placeIndex,
            Map<Long, Set<Long>> bookmarkUsersByPlace,
            Map<Long, Set<Long>> likeUsersByPlace,
            Map<Long, TrendProfile> trendProfilesByPlace,
            Map<PlacePairKey, SimilarityScore> snapshotScoresByPair,
            long totalBookmarkUserCount
    ) {
    }

    public record SimilarityScore(
            double geoKernel,
            double coBookmarkPmi,
            double coLikeCosine,
            double trendSimilarity,
            double totalSimilarity,
            boolean snapshotBacked
    ) {
        private static SimilarityScore empty() {
            return new SimilarityScore(0d, 0d, 0d, 0d, 0d, false);
        }

        private static SimilarityScore identity() {
            return new SimilarityScore(1d, 1d, 1d, 1d, 1d, false);
        }

        private static SimilarityScore snapshot(
                double geoKernel,
                double coBookmarkPmi,
                double coLikeCosine,
                double trendSimilarity,
                double totalSimilarity
        ) {
            return new SimilarityScore(
                    geoKernel,
                    coBookmarkPmi,
                    coLikeCosine,
                    trendSimilarity,
                    totalSimilarity,
                    true
            );
        }

        private static SimilarityScore realtime(
                double geoKernel,
                double coBookmarkPmi,
                double coLikeCosine,
                double trendSimilarity,
                double totalSimilarity
        ) {
            return new SimilarityScore(
                    geoKernel,
                    coBookmarkPmi,
                    coLikeCosine,
                    trendSimilarity,
                    totalSimilarity,
                    false
            );
        }
    }

    public record TrendProfile(long photoCount, double freshnessScore, boolean hasSignal) {
        private static TrendProfile of(long photoCount, LocalDateTime latestPostCreatedAt) {
            double freshnessScore = calculateFreshnessScore(latestPostCreatedAt);
            boolean hasSignal = photoCount > 0L || latestPostCreatedAt != null;
            return new TrendProfile(photoCount, freshnessScore, hasSignal);
        }

        static TrendProfile fallback(MapPlace place) {
            if (place == null) {
                return new TrendProfile(0L, 0d, false);
            }
            return of(place.currentPhotoCount(), null);
        }
    }

    public record PlacePairKey(Long leftPlaceId, Long rightPlaceId) {
        public static PlacePairKey of(Long firstPlaceId, Long secondPlaceId) {
            if (firstPlaceId == null || secondPlaceId == null) {
                return new PlacePairKey(firstPlaceId, secondPlaceId);
            }
            return firstPlaceId <= secondPlaceId
                    ? new PlacePairKey(firstPlaceId, secondPlaceId)
                    : new PlacePairKey(secondPlaceId, firstPlaceId);
        }
    }
}
