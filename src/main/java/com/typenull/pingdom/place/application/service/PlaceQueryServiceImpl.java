package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.api.dto.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.PlaceImageItem;
import com.typenull.pingdom.place.api.dto.PlaceListItem;
import com.typenull.pingdom.place.api.dto.PlaceListResponse;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
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
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private static final int PLACE_DETAIL_POST_LIMIT = 20;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listPlaces(int page, int limit, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        Page<MapPlace> placePage;
        if (keyword == null || keyword.isBlank()) {
            placePage = mapPlaceRepository.findAll(pageable);
        } else {
            placePage = mapPlaceRepository.findByNameContaining(keyword, pageable);
        }

        List<PlaceListItem> places = placePage.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        Pageable pageable = PageRequest.of(0, PLACE_DETAIL_POST_LIMIT,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<MapImage> postPage = mapImageRepository.findByMapPlace_Id(placeId, pageable);

        List<PlaceImageItem> posts = postPage.stream()
                .map(this::toImageItem)
                .toList();

        int postCount;
        if (postPage.size() < PLACE_DETAIL_POST_LIMIT) {
            postCount = postPage.size();
        } else {
            long totalCount = mapImageRepository.countByMapPlace_Id(placeId);
            postCount = totalCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCount;
        }

        return new PlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getRegistrant(),
                postCount,
                posts
        );
    }

    private PlaceListItem toListItem(MapPlace mapPlace) {
        return new PlaceListItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude()
        );
    }

    private PlaceImageItem toImageItem(MapImage mapImage) {
        return new PlaceImageItem(
                mapImage.getId(),
                mapImage.getImageUrl(),
                mapImage.getTitle(),
                mapImage.getDescription(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount()
        );
    }
}