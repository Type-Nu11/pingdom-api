package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceImageItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricSummary;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceQueryServiceImpl implements AdminMapPlaceQueryService {

    private static final int PLACE_DETAIL_POST_LIMIT = 20;
    private static final double CTR_PRIOR_WEIGHT = 8d;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceGrowthService placeGrowthService;

    //장소 전체 조회 기능 - 키워드를 받아서 검색 가능
    @Override
    @Transactional(readOnly = true)
    public AdminMapPlaceResponse listPlaces(int page, int limit, SortParam sortParam, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;

        Page<MapPlace> placePage = mapPlaceRepository.findByNameContaining(
                keyword,
                PageRequest.of(safePage - 1, safeLimit, toListSort(safeSortParam))
        );

        List<AdminMapPlaceItem> places = placePage.getContent()
                .stream()
                .map(this::toItem)
                .toList();

        return AdminMapPlaceResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    // 장소 상세 조회 기능 - 장소 정보를 불러오고 장소 내 사진들 키워드 검색 가능
    @Override
    @Transactional(readOnly = true)
    public AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam, String keyword) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        Sort sort = toSort(safeSortParam);
        Pageable latestPosts = PageRequest.of(0, PLACE_DETAIL_POST_LIMIT, sort);

        Page<MapImage> postPage = mapImageRepository.findByMapPlace_IdAndTitleContaining(placeId, keyword, latestPosts);

        List<AdminMapPlaceImageItem> posts = postPage.getContent().stream()
                .map(this::toImageItem)
                .toList();

        return new AdminMapPlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getUserId(),
                mapPlace.getRegistrant(),
                safeSortParam,
                Math.toIntExact(postPage.getTotalElements()),
                placeGrowthService.snapshot(mapPlace),
                posts
        );
    }

    @Override
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
        Integer safeDays = days == null || days <= 0 ? null : days;

        if (safeDays != null) {
            List<MapPlace> places = mapPlaceRepository.findByNameContaining(keyword, Pageable.unpaged()).getContent();
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
            double globalCtr = calculateGlobalCtr(
                    nullSafeCount(placeRecommendationSnapshotRepository.sumClickCount()),
                    nullSafeCount(placeRecommendationSnapshotRepository.sumExposureCount())
            );
            Page<MapPlace> placePage = findSnapshotMetricPage(
                    keyword,
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

        double globalCtr = calculateGlobalCtr(
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
                keyword,
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
                .map(place -> toRecommendationMetricItem(
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
                .map(place -> toRecommendationMetricItem(
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
            case SMOOTHED_CTR -> mapPlaceRepository.findRecommendationMetricPageOrderBySmoothedCtr(
                    keyword,
                    globalCtr,
                    CTR_PRIOR_WEIGHT,
                    pageable
            );
            case RAW_CTR -> mapPlaceRepository.findRecommendationMetricPageOrderByRawCtr(
                    keyword,
                    globalCtr,
                    CTR_PRIOR_WEIGHT,
                    pageable
            );
            case BOOKMARK_CONVERSION -> mapPlaceRepository.findRecommendationMetricPageOrderByBookmarkConversion(
                    keyword,
                    pageable
            );
            case LIKE_CONVERSION -> mapPlaceRepository.findRecommendationMetricPageOrderByLikeConversion(
                    keyword,
                    pageable
            );
            case TOTAL_CONVERSION -> mapPlaceRepository.findRecommendationMetricPageOrderByTotalConversion(
                    keyword,
                    pageable
            );
            case EXPOSURE -> mapPlaceRepository.findRecommendationMetricPageOrderByExposure(keyword, pageable);
            case CLICK -> mapPlaceRepository.findRecommendationMetricPageOrderByClick(keyword, pageable);
            case UPDATED_AT -> mapPlaceRepository.findRecommendationMetricPageOrderByUpdatedAt(keyword, pageable);
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
            case SMOOTHED_CTR -> mapPlaceRepository.findVersionRecommendationMetricPageOrderBySmoothedCtr(
                    keyword,
                    recommendationVersion,
                    globalCtr,
                    CTR_PRIOR_WEIGHT,
                    pageable
            );
            case RAW_CTR -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByRawCtr(
                    keyword,
                    recommendationVersion,
                    globalCtr,
                    CTR_PRIOR_WEIGHT,
                    pageable
            );
            case BOOKMARK_CONVERSION -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByBookmarkConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case LIKE_CONVERSION -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByLikeConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case TOTAL_CONVERSION -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByTotalConversion(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case EXPOSURE -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByExposure(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case CLICK -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByClick(
                    keyword,
                    recommendationVersion,
                    pageable
            );
            case UPDATED_AT -> mapPlaceRepository.findVersionRecommendationMetricPageOrderByUpdatedAt(
                    keyword,
                    recommendationVersion,
                    pageable
            );
        };
    }

    @Override
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

        List<MapPlace> places = mapPlaceRepository.findByNameContaining(safeKeyword, Pageable.unpaged()).getContent();
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
                toMetricSummary(safeBaselineVersion, baselineMetrics);
        AdminPlaceRecommendationMetricSummary targetSummary =
                toMetricSummary(safeTargetVersion, targetMetrics);
        AdminPlaceRecommendationMetricSummary deltaSummary =
                toDeltaSummary(safeBaselineVersion, baselineSummary, targetSummary);

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
        double globalCtr = calculateGlobalCtr(totalClickCount, totalExposureCount);

        return places.stream()
                .map(place -> {
                    ConversionCounts counts = conversionCounts.getOrDefault(place.getId(), new ConversionCounts());
                    return toRecommendationMetricItem(
                            place,
                            exposureCounts.getOrDefault(place.getId(), 0L),
                            clickCounts.getOrDefault(place.getId(), 0L),
                            counts.bookmarkConversionCount,
                            counts.likeConversionCount,
                            globalCtr,
                            null
                    );
                })
                .sorted(recommendationMetricComparator(sortBy))
                .toList();
    }

    private List<AdminPlaceRecommendationMetricItem> buildSnapshotMetrics(
            List<MapPlace> places,
            Map<Long, PlaceRecommendationSnapshot> snapshotsByPlaceId,
            RecommendationMetricSortBy sortBy
    ) {
        double globalCtr = calculateGlobalCtr(
                nullSafeCount(placeRecommendationSnapshotRepository.sumClickCount()),
                nullSafeCount(placeRecommendationSnapshotRepository.sumExposureCount())
        );

        return places.stream()
                .map(place -> toRecommendationMetricItem(
                        place,
                        snapshotsByPlaceId.get(place.getId()),
                        globalCtr
                ))
                .sorted(recommendationMetricComparator(sortBy))
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

        double globalCtr = calculateGlobalCtr(
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
                .map(place -> toRecommendationMetricItem(place, snapshotsByPlaceId.get(place.getId()), globalCtr))
                .sorted(recommendationMetricComparator(sortBy))
                .toList();
    }

    private Sort toSort(SortParam sortParam) {
        return switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private Sort toListSort(SortParam sortParam) {
        return switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("id"));
            case MOST_LIKED -> throw new AdminException(AdminErrorCode.UNSUPPORTED_PLACE_SORT_PARAM);
        };
    }

    private long nullSafeCount(Long value) {
        return value == null ? 0L : value;
    }

    private AdminMapPlaceItem toItem(MapPlace mapPlace) {
        return new AdminMapPlaceItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getUserId(),
                mapPlace.getRegistrant(),
                placeGrowthService.snapshot(mapPlace)
        );
    }

    private AdminMapPlaceImageItem toImageItem(MapImage mapImage) {
        return new AdminMapPlaceImageItem(
                mapImage.getId(),
                mapImage.getImageUrl(),
                mapImage.getTitle(),
                mapImage.getDescription(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount()
        );
    }

    private AdminPlaceRecommendationMetricItem toRecommendationMetricItem(
            MapPlace mapPlace,
            PlaceRecommendationSnapshot snapshot,
            double globalCtr
    ) {
        return toRecommendationMetricItem(
                mapPlace,
                snapshot == null ? 0L : snapshot.getExposureCount(),
                snapshot == null ? 0L : snapshot.getClickCount(),
                snapshot == null ? 0L : snapshot.getBookmarkConversionCount(),
                snapshot == null ? 0L : snapshot.getLikeConversionCount(),
                globalCtr,
                snapshot == null ? null : snapshot.getUpdatedAt()
        );
    }

    private AdminPlaceRecommendationMetricItem toRecommendationMetricItem(
            MapPlace mapPlace,
            PlaceRecommendationVersionSnapshot snapshot,
            double globalCtr
    ) {
        return toRecommendationMetricItem(
                mapPlace,
                snapshot == null ? 0L : snapshot.getExposureCount(),
                snapshot == null ? 0L : snapshot.getClickCount(),
                snapshot == null ? 0L : snapshot.getBookmarkConversionCount(),
                snapshot == null ? 0L : snapshot.getLikeConversionCount(),
                globalCtr,
                snapshot == null ? null : snapshot.getUpdatedAt()
        );
    }

    private AdminPlaceRecommendationMetricItem toRecommendationMetricItem(
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

    private AdminPlaceRecommendationMetricSummary toMetricSummary(
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

    private AdminPlaceRecommendationMetricSummary toDeltaSummary(
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

    private Comparator<AdminPlaceRecommendationMetricItem> recommendationMetricComparator(
            RecommendationMetricSortBy sortBy
    ) {
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

    private double calculateGlobalCtr(long totalClickCount, long totalExposureCount) {
        if (totalExposureCount <= 0L || totalClickCount <= 0L) {
            return 0d;
        }
        return (double) totalClickCount / (double) totalExposureCount;
    }
}
