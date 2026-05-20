package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapFavorite;
import com.typenull.pingdom.domain.map.dto.FavoriteCreateRequest;
import com.typenull.pingdom.domain.map.dto.FavoriteCreateResponse;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapFavoriteRepository;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapFavoriteService {

    private final MapFavoriteRepository mapFavoriteRepository;
    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public FavoriteCreateResponse createFavorite(FavoriteCreateRequest request, long userId) {
        Long placeId = request.placeId();

        boolean placeExists = mapPlaceRepository.existsById(placeId);
        if (!placeExists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        boolean alreadyExists = mapFavoriteRepository.existsByUserIdAndPlaceId(userId, placeId);
        if (alreadyExists) {
            throw new MapException(MapErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        MapFavorite favorite = MapFavorite.builder()
                .userId(userId)
                .placeId(placeId)
                .build();

        MapFavorite saved = mapFavoriteRepository.save(favorite);
        return new FavoriteCreateResponse(saved.getId(), saved.getPlaceId(), "장소 즐겨찾기를 추가했습니다.");
    }
}

