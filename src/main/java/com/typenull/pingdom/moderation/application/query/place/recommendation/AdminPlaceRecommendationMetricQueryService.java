package com.typenull.pingdom.moderation.application.query.place.recommendation;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceRecommendationMetricRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 이벤트 집계, 전체 누적 스냅샷, 버전 누적 스냅샷 중 조건에 맞는 지표 조회를 선택.
 * days가 null 또는 0 이하이면 누적 조회이며 기간 지표의 snapshotUpdatedAt은 null.
 * 정렬·페이지는 저장소에서 결정하고 페이지에 해당하는 장소의 상세 수치를 배치로 조합.
 */
@Service
@RequiredArgsConstructor
public class AdminPlaceRecommendationMetricQueryService {

    private final AdminPlaceRecommendationMetricRepository adminPlaceRecommendationMetricRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final AdminPlaceRecommendationMetricMapper metricMapper;
    private final AdminPlaceRecommendationMetricCountCollector metricCountCollector;
    private final AdminPlaceRecommendationMetricCompareQueryService compareQueryService;
    private final Clock clock;

    /**
     * 양수 days가 있으면 원본 이벤트의 기간 집계, 없으면 전체 또는 지정 버전의 누적 스냅샷 조회.
     * 조건과 페이지를 정규화하고 저장소의 지표 정렬 순서에 맞춰 세부 수치 조합. 집계 데이터는 조회에만 사용.
     */
    @Transactional(readOnly = true)
    public AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    ) {
        MetricSearchCondition condition = MetricSearchCondition.of(
                page,
                limit,
                sortBy,
                keyword,
                recommendationVersion,
                days
        );

        // 기간 지정은 누적 스냅샷으로 재현할 수 없으므로 원본 이벤트의 cutoff 집계를 우선.
        if (condition.days() != null) {
            return listPeriodMetrics(condition);
        }
        if (condition.recommendationVersion().isBlank()) {
            return listSnapshotMetrics(condition);
        }
        return listVersionSnapshotMetrics(condition);
    }

    @Transactional(readOnly = true)
    public AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    ) {
        return compareQueryService.compareRecommendationMetrics(baselineVersion, targetVersion, keyword, days);
    }

    private AdminPlaceRecommendationMetricsResponse listPeriodMetrics(MetricSearchCondition condition) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(condition.days());
        double globalCtr = metricCountCollector.calculatePeriodGlobalCtr(
                condition.keyword(),
                condition.recommendationVersion(),
                cutoff
        );
        Page<MapPlace> placePage = adminPlaceRecommendationMetricRepository.findPeriodRecommendationMetricPage(
                condition.keyword(),
                condition.recommendationVersion(),
                condition.sortBy().name(),
                globalCtr,
                AdminPlaceRecommendationMetricMapper.CTR_PRIOR_WEIGHT,
                cutoff,
                pageable(condition)
        );
        List<AdminPlaceRecommendationMetricItem> metrics = buildPeriodPageMetrics(
                placePage.getContent(),
                condition.recommendationVersion(),
                cutoff,
                globalCtr
        );

        return toResponse(condition, placePage, metrics);
    }

    private AdminPlaceRecommendationMetricsResponse listSnapshotMetrics(MetricSearchCondition condition) {
        double globalCtr = metricMapper.calculateGlobalCtr(
                nullSafeCount(placeRecommendationSnapshotRepository.sumClickCount()),
                nullSafeCount(placeRecommendationSnapshotRepository.sumExposureCount())
        );
        Page<MapPlace> placePage = findSnapshotMetricPage(
                condition.keyword(),
                condition.sortBy(),
                globalCtr,
                pageable(condition)
        );
        List<AdminPlaceRecommendationMetricItem> metrics = buildSnapshotPageMetrics(
                placePage.getContent(),
                globalCtr
        );

        return toResponse(condition, placePage, metrics);
    }

    private AdminPlaceRecommendationMetricsResponse listVersionSnapshotMetrics(MetricSearchCondition condition) {
        double globalCtr = metricMapper.calculateGlobalCtr(
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumClickCountByRecommendationVersion(condition.recommendationVersion())
                ),
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumExposureCountByRecommendationVersion(condition.recommendationVersion())
                )
        );
        Page<MapPlace> placePage = findVersionSnapshotMetricPage(
                condition.keyword(),
                condition.recommendationVersion(),
                condition.sortBy(),
                globalCtr,
                pageable(condition)
        );
        List<AdminPlaceRecommendationMetricItem> metrics = buildVersionSnapshotPageMetrics(
                placePage.getContent(),
                condition.recommendationVersion(),
                globalCtr
        );

        return toResponse(condition, placePage, metrics);
    }

    private AdminPlaceRecommendationMetricsResponse toResponse(
            MetricSearchCondition condition,
            Page<MapPlace> placePage,
            List<AdminPlaceRecommendationMetricItem> metrics
    ) {
        return AdminPlaceRecommendationMetricsResponse.of(
                metrics,
                condition.sortBy(),
                condition.recommendationVersion(),
                condition.days(),
                condition.page(),
                condition.limit(),
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    private List<AdminPlaceRecommendationMetricItem> buildSnapshotPageMetrics(
            List<MapPlace> places,
            double globalCtr
    ) {
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        Map<Long, PlaceRecommendationSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot : placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
            snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
        }

        return places.stream()
                .map(place -> metricMapper.toMetricItem(
                        place,
                        snapshotsByPlaceId.get(place.getId()),
                        globalCtr
                ))
                .toList();
    }

    private List<AdminPlaceRecommendationMetricItem> buildVersionSnapshotPageMetrics(
            List<MapPlace> places,
            String recommendationVersion,
            double globalCtr
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

        return places.stream()
                .map(place -> metricMapper.toMetricItem(
                        place,
                        snapshotsByPlaceId.get(place.getId()),
                        globalCtr
                ))
                .toList();
    }

    private List<AdminPlaceRecommendationMetricItem> buildPeriodPageMetrics(
            List<MapPlace> places,
            String recommendationVersion,
            LocalDateTime cutoff,
            double globalCtr
    ) {
        if (places.isEmpty()) {
            return List.of();
        }

        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        AdminPlaceRecommendationMetricCountCollector.MetricCounts metricCounts =
                metricCountCollector.collectPeriodMetrics(placeIds, recommendationVersion, cutoff);

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
                .toList();
    }

    private Page<MapPlace> findSnapshotMetricPage(
            String keyword,
            RecommendationMetricSortBy sortBy,
            double globalCtr,
            Pageable pageable
    ) {
        return switch (sortBy) {
            case SMOOTHED_CTR -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderBySmoothedCtr(
                    keyword,
                    globalCtr,
                    AdminPlaceRecommendationMetricMapper.CTR_PRIOR_WEIGHT,
                    pageable
            );
            case RAW_CTR -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByRawCtr(
                    keyword,
                    globalCtr,
                    AdminPlaceRecommendationMetricMapper.CTR_PRIOR_WEIGHT,
                    pageable
            );
            case BOOKMARK_CONVERSION -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByBookmarkConversion(
                    keyword,
                    pageable
            );
            case LIKE_CONVERSION -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByLikeConversion(
                    keyword,
                    pageable
            );
            case TOTAL_CONVERSION -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByTotalConversion(
                    keyword,
                    pageable
            );
            case EXPOSURE -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByExposure(keyword, pageable);
            case CLICK -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByClick(keyword, pageable);
            case UPDATED_AT -> adminPlaceRecommendationMetricRepository.findRecommendationMetricPageOrderByUpdatedAt(keyword, pageable);
        };
    }

    private Page<MapPlace> findVersionSnapshotMetricPage(
            String keyword,
            String recommendationVersion,
            RecommendationMetricSortBy sortBy,
            double globalCtr,
            Pageable pageable
    ) {
        return switch (sortBy) {
            case SMOOTHED_CTR -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderBySmoothedCtr(
                    keyword,
                    recommendationVersion,
                    globalCtr,
                    AdminPlaceRecommendationMetricMapper.CTR_PRIOR_WEIGHT,
                    pageable
            );
            case RAW_CTR -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByRawCtr(
                    keyword,
                    recommendationVersion,
                    globalCtr,
                    AdminPlaceRecommendationMetricMapper.CTR_PRIOR_WEIGHT,
                    pageable
            );
            case BOOKMARK_CONVERSION -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByBookmarkConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case LIKE_CONVERSION -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByLikeConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case TOTAL_CONVERSION -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByTotalConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case EXPOSURE -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByExposure(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case CLICK -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByClick(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case UPDATED_AT -> adminPlaceRecommendationMetricRepository.findVersionRecommendationMetricPageOrderByUpdatedAt(
                    keyword,
                    recommendationVersion,
                    pageable
            );
        };
    }

    private Pageable pageable(MetricSearchCondition condition) {
        return PageRequest.of(condition.page() - 1, condition.limit());
    }

    private long nullSafeCount(Long value) {
        return value == null ? 0L : value;
    }

    private record MetricSearchCondition(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    ) {

        private static MetricSearchCondition of(
                int page,
                int limit,
                RecommendationMetricSortBy sortBy,
                String keyword,
                String recommendationVersion,
                Integer days
        ) {
            return new MetricSearchCondition(
                    Math.max(page, 1),
                    Math.max(1, Math.min(limit, 100)),
                    sortBy == null ? RecommendationMetricSortBy.SMOOTHED_CTR : sortBy,
                    keyword == null ? "" : keyword.trim(),
                    recommendationVersion == null ? "" : recommendationVersion.trim(),
                    days == null || days <= 0 ? null : days
            );
        }
    }
}
