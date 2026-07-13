package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceImageItem;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceItem;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.domain.AdminPlaceSortParam;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminMapPlaceQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminMapPlaceQueryRepository.PlaceTouristCategoryProjection;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceLookupQueryService {

    private static final int PLACE_DETAIL_POST_LIMIT = 20;
    private static final String UNCATEGORIZED_CATEGORY_NAME = "미분류";

    private final MapPlaceRepository mapPlaceRepository;
    private final AdminMapPlaceQueryRepository adminMapPlaceQueryRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceGrowthService placeGrowthService;

    @Transactional(readOnly = true)
    public AdminMapPlaceResponse listPlaces(int page, int limit, AdminPlaceSortParam sortParam, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        AdminPlaceSortParam safeSortParam = sortParam == null ? AdminPlaceSortParam.LATEST : sortParam;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Long numericKeyword = parseLongKeyword(safeKeyword);

        Page<MapPlace> placePage = adminMapPlaceQueryRepository.searchAdminPlaces(
                safeKeyword,
                numericKeyword,
                PageRequest.of(safePage - 1, safeLimit, toListSort(safeSortParam))
        );

        Map<Long, Set<TouristCategory>> touristCategoriesByPlaceId = loadTouristCategories(
                placePage.getContent().stream().map(MapPlace::getId).toList()
        );
        List<AdminMapPlaceItem> places = placePage.getContent()
                .stream()
                .map(place -> toItem(
                        place,
                        touristCategoriesByPlaceId.getOrDefault(place.getId(), Set.of())
                ))
                .toList();

        return AdminMapPlaceResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam, String keyword) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        Sort sort = toSort(safeSortParam);
        Pageable latestPosts = PageRequest.of(0, PLACE_DETAIL_POST_LIMIT, sort);
        String safeKeyword = keyword == null ? "" : keyword.trim();

        Page<MapImage> postPage = mapImageRepository.findByMapPlace_IdAndTitleContaining(
                placeId,
                safeKeyword,
                latestPosts
        );

        List<AdminMapPlaceImageItem> posts = postPage.getContent().stream()
                .map(this::toImageItem)
                .toList();

        String category = toResponseCategory(mapPlace.getCategory());

        return new AdminMapPlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                category,
                toCategoryName(category),
                mapPlace.getEnglishName(),
                mapPlace.getTouristSummary(),
                mapPlace.currentTouristCategories(),
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

    private Sort toSort(SortParam sortParam) {
        return switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private Sort toListSort(AdminPlaceSortParam sortParam) {
        return switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("id"));
            case LEVEL_DESC -> Sort.by(Sort.Order.desc("photoCount"), Sort.Order.desc("id"));
        };
    }

    private AdminMapPlaceItem toItem(MapPlace mapPlace, Set<TouristCategory> touristCategories) {
        String category = toResponseCategory(mapPlace.getCategory());

        return new AdminMapPlaceItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                category,
                toCategoryName(category),
                mapPlace.getEnglishName(),
                mapPlace.getTouristSummary(),
                touristCategories,
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
                mapImage.getThumbnailUrl(),
                mapImage.getTitle(),
                mapImage.getDescription(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount()
        );
    }

    private String toResponseCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : null;
    }

    private String toCategoryName(String category) {
        return category == null ? UNCATEGORIZED_CATEGORY_NAME : category;
    }

    private Long parseLongKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Map<Long, Set<TouristCategory>> loadTouristCategories(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<TouristCategory>> categoriesByPlaceId = new HashMap<>();
        for (PlaceTouristCategoryProjection projection
                : adminMapPlaceQueryRepository.findTouristCategoriesByPlaceIds(placeIds)) {
            categoriesByPlaceId
                    .computeIfAbsent(
                            projection.getPlaceId(),
                            ignored -> EnumSet.noneOf(TouristCategory.class)
                    )
                    .add(projection.getTouristCategory());
        }
        return categoriesByPlaceId;
    }
}
