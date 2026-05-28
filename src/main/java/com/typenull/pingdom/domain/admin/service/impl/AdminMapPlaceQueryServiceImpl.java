package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceImageItem;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceItem;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminMapPlaceQueryService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.MapPlace;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceQueryServiceImpl implements AdminMapPlaceQueryService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;

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
    public AdminMapPlaceDetailResponse getPlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        List<AdminMapPlaceImageItem> posts = mapImageRepository.findByMapPlace_IdOrderByCreatedAtDesc(placeId)
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
                posts.size(),
                posts
        );
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
