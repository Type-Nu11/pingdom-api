package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceImageItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceItem;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.PlaceGrowthService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import java.util.List;
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

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceGrowthService placeGrowthService;

    //장소 전체 조회 기능 - 키워드를 받아서 검색 가능
    @Override
    @Transactional(readOnly = true)
    public AdminMapPlaceResponse listPlaces(int page, int limit, SortParam sortParam, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Page<MapPlace> placePage = mapPlaceRepository.findByNameContaining(
                keyword,
                PageRequest.of(safePage - 1, safeLimit, toListSort(sortParam == null ? SortParam.LATEST : sortParam))
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
            default -> Sort.by(Sort.Order.desc("id")); // MOST_LIKED나 LATEST는 기본적으로 id 역순 정렬로 대체하거나 적절히 처리
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
}
