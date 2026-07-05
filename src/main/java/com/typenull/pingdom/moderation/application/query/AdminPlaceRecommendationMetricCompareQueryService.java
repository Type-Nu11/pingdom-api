package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricSummary;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminMapPlaceQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPlaceRecommendationMetricCompareQueryService {

    private static final int MAX_PERIOD_METRIC_PLACE_COUNT = 10_000;

    private final AdminMapPlaceQueryRepository adminMapPlaceQueryRepository;
    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final AdminPlaceRecommendationMetricMapper metricMapper;
    private final AdminPlaceRecommendationMetricCountCollector metricCountCollector;
    private final Clock clock;

    AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    ) {
        CompareCondition condition = CompareCondition.of(baselineVersion, targetVersion, keyword, days);
        List<MapPlace> places = findMetricCandidatePlaces(condition.keyword());

        List<AdminPlaceRecommendationMetricItem> baselineMetrics;
        List<AdminPlaceRecommendationMetricItem> targetMetrics;
        if (places.isEmpty()) {
            baselineMetrics = List.of();
            targetMetrics = List.of();
        } else if (condition.days() != null) {
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(condition.days());
            baselineMetrics = buildPeriodFilteredMetrics(places, condition.baselineVersion(), cutoff);
            targetMetrics = buildPeriodFilteredMetrics(places, condition.targetVersion(), cutoff);
        } else {
            baselineMetrics = buildVersionFilteredMetrics(places, condition.baselineVersion());
            targetMetrics = buildVersionFilteredMetrics(places, condition.targetVersion());
        }

        AdminPlaceRecommendationMetricSummary baselineSummary =
                metricMapper.toMetricSummary(condition.baselineVersion(), baselineMetrics);
        AdminPlaceRecommendationMetricSummary targetSummary =
                metricMapper.toMetricSummary(condition.targetVersion(), targetMetrics);
        AdminPlaceRecommendationMetricSummary deltaSummary =
                metricMapper.toDeltaSummary(condition.baselineVersion(), baselineSummary, targetSummary);

        return new AdminPlaceRecommendationMetricsCompareResponse(
                condition.baselineVersion(),
                condition.targetVersion(),
                condition.days(),
                condition.keyword(),
                baselineSummary,
                targetSummary,
                deltaSummary
        );
    }

    private List<AdminPlaceRecommendationMetricItem> buildPeriodFilteredMetrics(
            List<MapPlace> places,
            String recommendationVersion,
            LocalDateTime cutoff
    ) {
        AdminPlaceRecommendationMetricCountCollector.MetricCounts metricCounts =
                new AdminPlaceRecommendationMetricCountCollector.MetricCounts();
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();

        for (int fromIndex = 0; fromIndex < placeIds.size();
                fromIndex += AdminPlaceRecommendationMetricCountCollector.PERIOD_METRIC_PLACE_BATCH_SIZE) {
            List<Long> batchPlaceIds = placeIds.subList(
                    fromIndex,
                    Math.min(
                            fromIndex + AdminPlaceRecommendationMetricCountCollector.PERIOD_METRIC_PLACE_BATCH_SIZE,
                            placeIds.size()
                    )
            );
            metricCountCollector.collectPeriodMetrics(
                    batchPlaceIds,
                    recommendationVersion,
                    cutoff,
                    metricCounts
            );
        }

        double globalCtr = metricMapper.calculateGlobalCtr(
                metricCounts.totalClickCount(),
                metricCounts.totalExposureCount()
        );

        return places.stream()
                .map(place -> {
                    AdminPlaceRecommendationMetricCountCollector.ConversionCounts conversionCounts =
                            metricCounts.conversionCounts(place.getId());
                    return metricMapper.toMetricItem(
                            place,
                            metricCounts.exposureCount(place.getId()),
                            metricCounts.clickCount(place.getId()),
                            conversionCounts.bookmarkConversionCount(),
                            conversionCounts.likeConversionCount(),
                            globalCtr,
                            null
                    );
                })
                .sorted(metricMapper.comparator(RecommendationMetricSortBy.CLICK))
                .toList();
    }

    private List<AdminPlaceRecommendationMetricItem> buildVersionFilteredMetrics(
            List<MapPlace> places,
            String recommendationVersion
    ) {
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        Map<Long, PlaceRecommendationVersionSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (int fromIndex = 0; fromIndex < placeIds.size();
                fromIndex += AdminPlaceRecommendationMetricCountCollector.PERIOD_METRIC_PLACE_BATCH_SIZE) {
            List<Long> batchPlaceIds = placeIds.subList(
                    fromIndex,
                    Math.min(
                            fromIndex + AdminPlaceRecommendationMetricCountCollector.PERIOD_METRIC_PLACE_BATCH_SIZE,
                            placeIds.size()
                    )
            );
            for (PlaceRecommendationVersionSnapshot snapshot :
                    placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                            batchPlaceIds,
                            recommendationVersion
                    )) {
                snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
            }
        }

        double globalCtr = metricMapper.calculateGlobalCtr(
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumClickCountByRecommendationVersion(recommendationVersion)
                ),
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumExposureCountByRecommendationVersion(recommendationVersion)
                )
        );

        return places.stream()
                .map(place -> metricMapper.toMetricItem(place, snapshotsByPlaceId.get(place.getId()), globalCtr))
                .sorted(metricMapper.comparator(RecommendationMetricSortBy.CLICK))
                .toList();
    }

    private List<MapPlace> findMetricCandidatePlaces(String keyword) {
        Page<MapPlace> firstPage = adminMapPlaceQueryRepository.findByNameContaining(
                keyword,
                metricCandidatePageable(0)
        );
        if (firstPage.getTotalElements() > MAX_PERIOD_METRIC_PLACE_COUNT) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_METRIC_QUERY_TOO_LARGE);
        }

        List<MapPlace> places = new ArrayList<>(Math.toIntExact(firstPage.getTotalElements()));
        places.addAll(firstPage.getContent());
        for (int page = 1; page < firstPage.getTotalPages(); page++) {
            places.addAll(adminMapPlaceQueryRepository.findByNameContaining(
                    keyword,
                    metricCandidatePageable(page)
            ).getContent());
        }
        return places;
    }

    private Pageable metricCandidatePageable(int page) {
        return PageRequest.of(
                page,
                AdminPlaceRecommendationMetricCountCollector.PERIOD_METRIC_PLACE_BATCH_SIZE,
                Sort.by(Sort.Order.asc("id"))
        );
    }

    private long nullSafeCount(Long value) {
        return value == null ? 0L : value;
    }

    private record CompareCondition(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    ) {

        private static CompareCondition of(
                String baselineVersion,
                String targetVersion,
                String keyword,
                Integer days
        ) {
            return new CompareCondition(
                    baselineVersion == null ? "" : baselineVersion.trim(),
                    targetVersion == null ? "" : targetVersion.trim(),
                    keyword == null ? "" : keyword.trim(),
                    days == null || days <= 0 ? null : days
            );
        }
    }
}
