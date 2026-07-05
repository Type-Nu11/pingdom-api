package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceRecommendationMetricRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPlaceRecommendationMetricCountCollector {

    static final int PERIOD_METRIC_PLACE_BATCH_SIZE = 500;

    private final AdminPlaceRecommendationMetricRepository adminPlaceRecommendationMetricRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final AdminPlaceRecommendationMetricMapper metricMapper;

    double calculatePeriodGlobalCtr(
            String keyword,
            String recommendationVersion,
            LocalDateTime cutoff
    ) {
        AdminPlaceRecommendationMetricRepository.PeriodMetricCountProjection totals =
                adminPlaceRecommendationMetricRepository.sumPeriodMetricCounts(
                        keyword,
                        recommendationVersion,
                        cutoff
                );
        if (totals == null) {
            return 0d;
        }
        return metricMapper.calculateGlobalCtr(
                nullSafeCount(totals.getClickCount()),
                nullSafeCount(totals.getExposureCount())
        );
    }

    MetricCounts collectPeriodMetrics(
            List<Long> placeIds,
            String recommendationVersion,
            LocalDateTime cutoff
    ) {
        MetricCounts counts = new MetricCounts();
        collectPeriodMetrics(placeIds, recommendationVersion, cutoff, counts);
        return counts;
    }

    void collectPeriodMetrics(
            List<Long> placeIds,
            String recommendationVersion,
            LocalDateTime cutoff,
            MetricCounts counts
    ) {
        if (placeIds.isEmpty()) {
            return;
        }
        if (recommendationVersion.isBlank()) {
            collectAllVersionPeriodMetrics(placeIds, cutoff, counts);
            return;
        }
        collectVersionPeriodMetrics(placeIds, recommendationVersion, cutoff, counts);
    }

    private void collectAllVersionPeriodMetrics(
            List<Long> placeIds,
            LocalDateTime cutoff,
            MetricCounts counts
    ) {
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIdsAndCreatedAtGreaterThanEqual(
                        placeIds,
                        cutoff
                )) {
            counts.putExposureCount(projection.getPlaceId(), projection.getExposureCount());
        }
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository.countClicksByPlaceIdsAndCreatedAtGreaterThanEqual(
                        placeIds,
                        cutoff
                )) {
            counts.putClickCount(projection.getPlaceId(), projection.getClickCount());
        }
        for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                placeRecommendationConversionRepository.countConversionsByPlaceIdsAndCreatedAtGreaterThanEqual(
                        placeIds,
                        cutoff
                )) {
            counts.accumulateConversion(
                    projection.getPlaceId(),
                    projection.getConversionType(),
                    projection.getConversionCount()
            );
        }
    }

    private void collectVersionPeriodMetrics(
            List<Long> placeIds,
            String recommendationVersion,
            LocalDateTime cutoff,
            MetricCounts counts
    ) {
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository
                        .countExposuresByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                placeIds,
                                recommendationVersion,
                                cutoff
                        )) {
            counts.putExposureCount(projection.getPlaceId(), projection.getExposureCount());
        }
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository
                        .countClicksByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                placeIds,
                                recommendationVersion,
                                cutoff
                        )) {
            counts.putClickCount(projection.getPlaceId(), projection.getClickCount());
        }
        for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                placeRecommendationConversionRepository
                        .countConversionsByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                placeIds,
                                recommendationVersion,
                                cutoff
                        )) {
            counts.accumulateConversion(
                    projection.getPlaceId(),
                    projection.getConversionType(),
                    projection.getConversionCount()
            );
        }
    }

    private long nullSafeCount(Long value) {
        return value == null ? 0L : value;
    }

    static class MetricCounts {
        private final Map<Long, Long> exposureCounts = new HashMap<>();
        private final Map<Long, Long> clickCounts = new HashMap<>();
        private final Map<Long, ConversionCounts> conversionCounts = new HashMap<>();

        long exposureCount(Long placeId) {
            return exposureCounts.getOrDefault(placeId, 0L);
        }

        long clickCount(Long placeId) {
            return clickCounts.getOrDefault(placeId, 0L);
        }

        ConversionCounts conversionCounts(Long placeId) {
            return conversionCounts.getOrDefault(placeId, new ConversionCounts());
        }

        long totalExposureCount() {
            return exposureCounts.values().stream().mapToLong(Long::longValue).sum();
        }

        long totalClickCount() {
            return clickCounts.values().stream().mapToLong(Long::longValue).sum();
        }

        private void putExposureCount(Long placeId, Long exposureCount) {
            exposureCounts.put(placeId, exposureCount);
        }

        private void putClickCount(Long placeId, Long clickCount) {
            clickCounts.put(placeId, clickCount);
        }

        private void accumulateConversion(
                Long placeId,
                PlaceRecommendationConversionType conversionType,
                long count
        ) {
            conversionCounts.computeIfAbsent(placeId, ignored -> new ConversionCounts())
                    .accumulate(conversionType, count);
        }
    }

    static class ConversionCounts {
        private long bookmarkConversionCount;
        private long likeConversionCount;

        long bookmarkConversionCount() {
            return bookmarkConversionCount;
        }

        long likeConversionCount() {
            return likeConversionCount;
        }

        private void accumulate(PlaceRecommendationConversionType conversionType, long count) {
            if (conversionType == PlaceRecommendationConversionType.BOOKMARK) {
                bookmarkConversionCount += count;
                return;
            }
            if (conversionType == PlaceRecommendationConversionType.LIKE) {
                likeConversionCount += count;
            }
        }
    }
}
