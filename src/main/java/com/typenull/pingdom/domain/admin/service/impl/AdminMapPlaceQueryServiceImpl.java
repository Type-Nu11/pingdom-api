package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceImageItem;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceItem;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminMapPlaceQueryService;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.place.domain.repository.MapPlaceRepository;
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
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminMapPlaceResponse listPlaces(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Page<MapPlace> placePage = mapPlaceRepository.findAll(
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "id"))
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

    @Override
    @Transactional(readOnly = true)
    public AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        long totalPostCount = mapImageRepository.countByMapPlace_Id(placeId);
        Sort sort = toSort(safeSortParam);
        Pageable latestPosts = PageRequest.of(0, PLACE_DETAIL_POST_LIMIT, sort);
        String username = mapPlace.getUserId() == null ? null
                : userRepository.findById(mapPlace.getUserId())
                .map(user -> user.getUsername())
                .orElse(null);

        List<AdminMapPlaceImageItem> posts = mapImageRepository.findByMapPlace_Id(placeId, latestPosts)
                .stream()
                .map(this::toImageItem)
                .toList();

        return new AdminMapPlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getUserId(),
                username,
                safeSortParam,
                Math.toIntExact(totalPostCount),
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

    private AdminMapPlaceItem toItem(MapPlace mapPlace) {
        return new AdminMapPlaceItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getUserId()
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
