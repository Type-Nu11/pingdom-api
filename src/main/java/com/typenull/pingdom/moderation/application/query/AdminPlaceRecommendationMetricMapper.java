package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricSummary;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationVersionSnapshot;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminPlaceRecommendationMetricMapper {

    static final double CTR_PRIOR_WEIGHT = 8d;

    AdminPlaceRecommendationMetricItem toMetricItem(
            MapPlace mapPlace,
            PlaceRecommendationSnapshot snapshot,
            double globalCtr
    ) {
        return toMetricItem(
                mapPlace,
                snapshot == null ? 0L : snapshot.getExposureCount(),
                snapshot == null ? 0L : snapshot.getClickCount(),
                snapshot == null ? 0L : snapshot.getBookmarkConversionCount(),
                snapshot == null ? 0L : snapshot.getLikeConversionCount(),
                globalCtr,
                snapshot == null ? null : snapshot.getUpdatedAt()
        );
    }

    AdminPlaceRecommendationMetricItem toMetricItem(
            MapPlace mapPlace,
            PlaceRecommendationVersionSnapshot snapshot,
            double globalCtr
    ) {
        return toMetricItem(
                mapPlace,
                snapshot == null ? 0L : snapshot.getExposureCount(),
                snapshot == null ? 0L : snapshot.getClickCount(),
                snapshot == null ? 0L : snapshot.getBookmarkConversionCount(),
                snapshot == null ? 0L : snapshot.getLikeConversionCount(),
                globalCtr,
                snapshot == null ? null : snapshot.getUpdatedAt()
        );
    }

    AdminPlaceRecommendationMetricItem toMetricItem(
            MapPlace mapPlace,
            long exposureCount,
            long clickCount,
            long bookmarkConversionCount,
            long likeConversionCount,
            double globalCtr,
            LocalDateTime snapshotUpdatedAt
    ) {
        double rawCtr = exposureCount <= 0L ? 0d : (double) clickCount / (double) exposureCount;
        double smoothedCtr = exposureCount <= 0L
                ? 0d
                : (clickCount + (CTR_PRIOR_WEIGHT * globalCtr)) / (exposureCount + CTR_PRIOR_WEIGHT);
        double bookmarkConversionRate = exposureCount <= 0L
                ? 0d
                : (double) bookmarkConversionCount / (double) exposureCount;
        double likeConversionRate = exposureCount <= 0L
                ? 0d
                : (double) likeConversionCount / (double) exposureCount;
        double totalConversionRate = exposureCount <= 0L
                ? 0d
                : (double) (bookmarkConversionCount + likeConversionCount) / (double) exposureCount;

        return new AdminPlaceRecommendationMetricItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.currentPhotoCount(),
                exposureCount,
                clickCount,
                rawCtr,
                smoothedCtr,
                bookmarkConversionCount,
                likeConversionCount,
                bookmarkConversionRate,
                likeConversionRate,
                totalConversionRate,
                snapshotUpdatedAt
        );
    }

    AdminPlaceRecommendationMetricSummary toMetricSummary(
            String recommendationVersion,
            List<AdminPlaceRecommendationMetricItem> metrics
    ) {
        long exposureCount = metrics.stream().mapToLong(AdminPlaceRecommendationMetricItem::exposureCount).sum();
        long clickCount = metrics.stream().mapToLong(AdminPlaceRecommendationMetricItem::clickCount).sum();
        long bookmarkConversionCount = metrics.stream()
                .mapToLong(AdminPlaceRecommendationMetricItem::bookmarkConversionCount)
                .sum();
        long likeConversionCount = metrics.stream()
                .mapToLong(AdminPlaceRecommendationMetricItem::likeConversionCount)
                .sum();
        double rawCtr = exposureCount <= 0L ? 0d : (double) clickCount / (double) exposureCount;
        double smoothedCtr = rawCtr;
        double bookmarkConversionRate = exposureCount <= 0L
                ? 0d
                : (double) bookmarkConversionCount / (double) exposureCount;
        double likeConversionRate = exposureCount <= 0L
                ? 0d
                : (double) likeConversionCount / (double) exposureCount;
        double totalConversionRate = exposureCount <= 0L
                ? 0d
                : (double) (bookmarkConversionCount + likeConversionCount) / (double) exposureCount;

        return new AdminPlaceRecommendationMetricSummary(
                recommendationVersion,
                exposureCount,
                clickCount,
                rawCtr,
                smoothedCtr,
                bookmarkConversionCount,
                likeConversionCount,
                bookmarkConversionRate,
                likeConversionRate,
                totalConversionRate
        );
    }

    AdminPlaceRecommendationMetricSummary toDeltaSummary(
            String baselineVersion,
            AdminPlaceRecommendationMetricSummary baseline,
            AdminPlaceRecommendationMetricSummary target
    ) {
        return new AdminPlaceRecommendationMetricSummary(
                target.recommendationVersion() + " - " + baselineVersion,
                target.exposureCount() - baseline.exposureCount(),
                target.clickCount() - baseline.clickCount(),
                target.rawCtr() - baseline.rawCtr(),
                target.smoothedCtr() - baseline.smoothedCtr(),
                target.bookmarkConversionCount() - baseline.bookmarkConversionCount(),
                target.likeConversionCount() - baseline.likeConversionCount(),
                target.bookmarkConversionRate() - baseline.bookmarkConversionRate(),
                target.likeConversionRate() - baseline.likeConversionRate(),
                target.totalConversionRate() - baseline.totalConversionRate()
        );
    }

    Comparator<AdminPlaceRecommendationMetricItem> comparator(RecommendationMetricSortBy sortBy) {
        return switch (sortBy) {
            case RAW_CTR -> Comparator.comparingDouble(AdminPlaceRecommendationMetricItem::rawCtr)
                    .thenComparingDouble(AdminPlaceRecommendationMetricItem::smoothedCtr)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case BOOKMARK_CONVERSION -> Comparator.comparingDouble(AdminPlaceRecommendationMetricItem::bookmarkConversionRate)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::bookmarkConversionCount)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case LIKE_CONVERSION -> Comparator.comparingDouble(AdminPlaceRecommendationMetricItem::likeConversionRate)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::likeConversionCount)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case TOTAL_CONVERSION -> Comparator.comparingDouble(AdminPlaceRecommendationMetricItem::totalConversionRate)
                    .thenComparingLong(item -> item.bookmarkConversionCount() + item.likeConversionCount())
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case EXPOSURE -> Comparator.comparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::clickCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case CLICK -> Comparator.comparingLong(AdminPlaceRecommendationMetricItem::clickCount)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::exposureCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case UPDATED_AT -> Comparator.comparing(
                            AdminPlaceRecommendationMetricItem::snapshotUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
            case SMOOTHED_CTR -> Comparator.comparingDouble(AdminPlaceRecommendationMetricItem::smoothedCtr)
                    .thenComparingDouble(AdminPlaceRecommendationMetricItem::rawCtr)
                    .thenComparingLong(AdminPlaceRecommendationMetricItem::clickCount)
                    .reversed()
                    .thenComparing(AdminPlaceRecommendationMetricItem::id);
        };
    }

    double calculateGlobalCtr(long totalClickCount, long totalExposureCount) {
        if (totalExposureCount <= 0L || totalClickCount <= 0L) {
            return 0d;
        }
        return (double) totalClickCount / (double) totalExposureCount;
    }
}
