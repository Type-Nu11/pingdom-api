package com.typenull.pingdom.moderation.application.query.place.recommendation;

import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
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

/**
 * 장소별 노출·클릭과 BOOKMARK·LIKE 전환 이벤트 수를 수집합니다.
 * cutoff 이상을 포함하고 공백 버전은 전체 버전으로 취급합니다. 결과에 없는 장소의 수는 0으로 보충합니다.
 * 누적 객체에 노출·클릭은 덮어쓰고 전환은 더하므로 같은 장소 배치를 반복해서 넣지 않아야 합니다.
 */
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
