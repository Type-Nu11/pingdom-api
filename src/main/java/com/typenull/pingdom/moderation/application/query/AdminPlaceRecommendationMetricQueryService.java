package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricSummary;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminMapPlaceQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceRecommendationMetricRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
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

@Service
@RequiredArgsConstructor
public class AdminPlaceRecommendationMetricQueryService {

    private final AdminMapPlaceQueryRepository adminMapPlaceQueryRepository;
    private final AdminPlaceRecommendationMetricRepository adminPlaceRecommendationMetricRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final AdminPlaceRecommendationMetricMapper metricMapper;

    @Transactional(readOnly = true)
    public AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    ) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        RecommendationMetricSortBy safeSortBy = sortBy == null ? RecommendationMetricSortBy.SMOOTHED_CTR : sortBy;
        String safeRecommendationVersion = recommendationVersion == null ? "" : recommendationVersion.trim();
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeDays = days == null || days <= 0 ? null : days;

        if (safeDays != null) {
            List<MapPlace> places = adminMapPlaceQueryRepository.findByNameContaining(safeKeyword, Pageable.unpaged()).getContent();
            List<Long> placeIds = places.stream()
                    .map(MapPlace::getId)
                    .toList();

            if (placeIds.isEmpty()) {
                return AdminPlaceRecommendationMetricsResponse.of(
                        List.of(),
                        safeSortBy,
                        safeRecommendationVersion,
                        safeDays,
                        safePage,
                        safeLimit,
                        0L,
                        0L
                );
            }

            List<AdminPlaceRecommendationMetricItem> sortedMetrics = buildPeriodFilteredMetrics(
                    places,
                    safeRecommendationVersion,
                    safeSortBy,
                    LocalDateTime.now().minusDays(safeDays)
            );
            long totalCount = sortedMetrics.size();
            long totalPages = totalCount == 0L ? 0L : (long) Math.ceil((double) totalCount / (double) safeLimit);
            int fromIndex = Math.min((safePage - 1) * safeLimit, sortedMetrics.size());
            int toIndex = Math.min(fromIndex + safeLimit, sortedMetrics.size());
            List<AdminPlaceRecommendationMetricItem> pagedMetrics = sortedMetrics.subList(fromIndex, toIndex);

            return AdminPlaceRecommendationMetricsResponse.of(
                    pagedMetrics,
                    safeSortBy,
                    safeRecommendationVersion,
                    safeDays,
                    safePage,
                    safeLimit,
                    totalCount,
                    totalPages
            );
        }

        if (safeRecommendationVersion.isBlank()) {
            double globalCtr = metricMapper.calculateGlobalCtr(
                    nullSafeCount(placeRecommendationSnapshotRepository.sumClickCount()),
                    nullSafeCount(placeRecommendationSnapshotRepository.sumExposureCount())
            );
            Page<MapPlace> placePage = findSnapshotMetricPage(
                    safeKeyword,
                    safeSortBy,
                    globalCtr,
                    PageRequest.of(safePage - 1, safeLimit)
            );
            List<AdminPlaceRecommendationMetricItem> metrics = buildSnapshotPageMetrics(
                    placePage.getContent(),
                    globalCtr
            );

            return AdminPlaceRecommendationMetricsResponse.of(
                    metrics,
                    safeSortBy,
                    safeRecommendationVersion,
                    safeDays,
                    safePage,
                    safeLimit,
                    placePage.getTotalElements(),
                    placePage.getTotalPages()
            );
        }

        double globalCtr = metricMapper.calculateGlobalCtr(
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumClickCountByRecommendationVersion(safeRecommendationVersion)
                ),
                nullSafeCount(
                        placeRecommendationVersionSnapshotRepository
                                .sumExposureCountByRecommendationVersion(safeRecommendationVersion)
                )
        );
        Page<MapPlace> placePage = findVersionSnapshotMetricPage(
                safeKeyword,
                safeRecommendationVersion,
                safeSortBy,
                globalCtr,
                PageRequest.of(safePage - 1, safeLimit)
        );
        List<AdminPlaceRecommendationMetricItem> metrics = buildVersionSnapshotPageMetrics(
                placePage.getContent(),
                safeRecommendationVersion,
                globalCtr
        );

        return AdminPlaceRecommendationMetricsResponse.of(
                metrics,
                safeSortBy,
                safeRecommendationVersion,
                safeDays,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    ) {
        String safeBaselineVersion = baselineVersion == null ? "" : baselineVersion.trim();
        String safeTargetVersion = targetVersion == null ? "" : targetVersion.trim();
        String safeKeyword = keyword == null ? "" : keyword;
        Integer safeDays = days == null || days <= 0 ? null : days;

        List<MapPlace> places = adminMapPlaceQueryRepository.findByNameContaining(safeKeyword, Pageable.unpaged()).getContent();
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();

        List<AdminPlaceRecommendationMetricItem> baselineMetrics;
        List<AdminPlaceRecommendationMetricItem> targetMetrics;
        if (placeIds.isEmpty()) {
            baselineMetrics = List.of();
            targetMetrics = List.of();
        } else if (safeDays != null) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);
            baselineMetrics = buildPeriodFilteredMetrics(
                    places,
                    safeBaselineVersion,
                    RecommendationMetricSortBy.CLICK,
                    cutoff
            );
            targetMetrics = buildPeriodFilteredMetrics(
                    places,
                    safeTargetVersion,
                    RecommendationMetricSortBy.CLICK,
                    cutoff
            );
        } else {
            baselineMetrics = buildVersionFilteredMetrics(
                    places,
                    safeBaselineVersion,
                    RecommendationMetricSortBy.CLICK
            );
            targetMetrics = buildVersionFilteredMetrics(
                    places,
                    safeTargetVersion,
                    RecommendationMetricSortBy.CLICK
            );
        }

        AdminPlaceRecommendationMetricSummary baselineSummary =
                metricMapper.toMetricSummary(safeBaselineVersion, baselineMetrics);
        AdminPlaceRecommendationMetricSummary targetSummary =
                metricMapper.toMetricSummary(safeTargetVersion, targetMetrics);
        AdminPlaceRecommendationMetricSummary deltaSummary =
                metricMapper.toDeltaSummary(safeBaselineVersion, baselineSummary, targetSummary);

        return new AdminPlaceRecommendationMetricsCompareResponse(
                safeBaselineVersion,
                safeTargetVersion,
                safeDays,
                safeKeyword,
                baselineSummary,
                targetSummary,
                deltaSummary
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
        for (PlaceRecommendationVersionSnapshot snapshot :
                placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                        placeIds,
                        recommendationVersion
                )) {
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

    private List<AdminPlaceRecommendationMetricItem> buildPeriodFilteredMetrics(
            List<MapPlace> places,
            String recommendationVersion,
            RecommendationMetricSortBy sortBy,
            LocalDateTime cutoff
    ) {
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        Map<Long, Long> exposureCounts = new HashMap<>();
        Map<Long, Long> clickCounts = new HashMap<>();
        Map<Long, ConversionCounts> conversionCounts = new HashMap<>();

        if (recommendationVersion.isBlank()) {
            for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                    placeRecommendationExposureRepository.countExposuresByPlaceIdsAndCreatedAtGreaterThanEqual(
                            placeIds,
                            cutoff
                    )) {
                exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
            }
            for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                    placeRecommendationClickRepository.countClicksByPlaceIdsAndCreatedAtGreaterThanEqual(
                            placeIds,
                            cutoff
                    )) {
                clickCounts.put(projection.getPlaceId(), projection.getClickCount());
            }
            for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                    placeRecommendationConversionRepository.countConversionsByPlaceIdsAndCreatedAtGreaterThanEqual(
                            placeIds,
                            cutoff
                    )) {
                conversionCounts.computeIfAbsent(projection.getPlaceId(), ignored -> new ConversionCounts())
                        .accumulate(projection.getConversionType(), projection.getConversionCount());
            }
        } else {
            for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                    placeRecommendationExposureRepository
                            .countExposuresByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                    placeIds,
                                    recommendationVersion,
                                    cutoff
                            )) {
                exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
            }
            for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                    placeRecommendationClickRepository
                            .countClicksByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                    placeIds,
                                    recommendationVersion,
                                    cutoff
                            )) {
                clickCounts.put(projection.getPlaceId(), projection.getClickCount());
            }
            for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                    placeRecommendationConversionRepository
                            .countConversionsByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
                                    placeIds,
                                    recommendationVersion,
                                    cutoff
                            )) {
                conversionCounts.computeIfAbsent(projection.getPlaceId(), ignored -> new ConversionCounts())
                        .accumulate(projection.getConversionType(), projection.getConversionCount());
            }
        }

        long totalExposureCount = exposureCounts.values().stream().mapToLong(Long::longValue).sum();
        long totalClickCount = clickCounts.values().stream().mapToLong(Long::longValue).sum();
        double globalCtr = metricMapper.calculateGlobalCtr(totalClickCount, totalExposureCount);

        return places.stream()
                .map(place -> {
                    ConversionCounts counts = conversionCounts.getOrDefault(place.getId(), new ConversionCounts());
                    return metricMapper.toMetricItem(
                            place,
                            exposureCounts.getOrDefault(place.getId(), 0L),
                            clickCounts.getOrDefault(place.getId(), 0L),
                            counts.bookmarkConversionCount,
                            counts.likeConversionCount,
                            globalCtr,
                            null
                    );
                })
                .sorted(metricMapper.comparator(sortBy))
                .toList();
    }

    private List<AdminPlaceRecommendationMetricItem> buildVersionFilteredMetrics(
            List<MapPlace> places,
            String recommendationVersion,
            RecommendationMetricSortBy sortBy
    ) {
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        Map<Long, PlaceRecommendationVersionSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot snapshot :
                placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                        placeIds,
                        recommendationVersion
                )) {
            snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
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
                .sorted(metricMapper.comparator(sortBy))
                .toList();
    }

    private long nullSafeCount(Long value) {
        return value == null ? 0L : value;
    }

    private static class ConversionCounts {
        private long bookmarkConversionCount;
        private long likeConversionCount;

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
