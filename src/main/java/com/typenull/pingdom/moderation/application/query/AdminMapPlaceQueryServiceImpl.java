package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceImageItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.PlaceGrowthService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
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
            String keyword
    ) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        RecommendationMetricSortBy safeSortBy = sortBy == null ? RecommendationMetricSortBy.SMOOTHED_CTR : sortBy;

        List<MapPlace> places = mapPlaceRepository.findByNameContaining(keyword, Pageable.unpaged()).getContent();
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();

        Map<Long, PlaceRecommendationSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot : placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
            snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
        }

        double globalCtr = calculateGlobalCtr(
                placeRecommendationSnapshotRepository.sumClickCount(),
                placeRecommendationSnapshotRepository.sumExposureCount()
        );

        List<AdminPlaceRecommendationMetricItem> sortedMetrics = places.stream()
                .map(place -> toRecommendationMetricItem(place, snapshotsByPlaceId.get(place.getId()), globalCtr))
                .sorted(recommendationMetricComparator(safeSortBy))
                .toList();

        long totalCount = sortedMetrics.size();
        long totalPages = totalCount == 0L ? 0L : (long) Math.ceil((double) totalCount / (double) safeLimit);
        int fromIndex = Math.min((safePage - 1) * safeLimit, sortedMetrics.size());
        int toIndex = Math.min(fromIndex + safeLimit, sortedMetrics.size());
        List<AdminPlaceRecommendationMetricItem> pagedMetrics = sortedMetrics.subList(fromIndex, toIndex);

        return AdminPlaceRecommendationMetricsResponse.of(
                pagedMetrics,
                safeSortBy,
                safePage,
                safeLimit,
                totalCount,
                totalPages
        );
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
        long exposureCount = snapshot == null ? 0L : snapshot.getExposureCount();
        long clickCount = snapshot == null ? 0L : snapshot.getClickCount();
        double rawCtr = exposureCount <= 0L ? 0d : (double) clickCount / (double) exposureCount;
        double smoothedCtr = exposureCount <= 0L
                ? 0d
                : (clickCount + (CTR_PRIOR_WEIGHT * globalCtr)) / (exposureCount + CTR_PRIOR_WEIGHT);

        return new AdminPlaceRecommendationMetricItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.currentPhotoCount(),
                exposureCount,
                clickCount,
                rawCtr,
                smoothedCtr,
                snapshot == null ? null : snapshot.getUpdatedAt()
        );
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
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .reversed()
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
