package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.place.AdminPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.map.domain.MapPlace;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public void deletePlace(long placeId) {
        boolean exists = mapPlaceRepository.existsById(placeId);
        if (!exists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        mapPlaceRepository.deleteById(placeId);
    }

    @Transactional
    public AdminPlaceKakaoPlaceIdUpdateResponse updateKakaoPlaceId(Long adminUserId, long placeId, String kakaoPlaceId) {
        String normalizedKakaoPlaceId = normalizeKakaoPlaceId(kakaoPlaceId);

        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        mapPlaceRepository.findByKakaoPlaceId(normalizedKakaoPlaceId)
                .filter(connectedPlace -> !Objects.equals(connectedPlace.getId(), mapPlace.getId()))
                .ifPresent(connectedPlace -> {
                    throw new AdminException(AdminErrorCode.KAKAO_PLACE_ID_ALREADY_CONNECTED);
                });

        String previousKakaoPlaceId = mapPlace.getKakaoPlaceId();
        mapPlace.updateKakaoPlaceId(normalizedKakaoPlaceId);

        log.info(
                "Admin updated kakaoPlaceId. adminUserId={}, placeId={}, beforeKakaoPlaceId={}, afterKakaoPlaceId={}",
                adminUserId,
                placeId,
                previousKakaoPlaceId,
                normalizedKakaoPlaceId
        );

        return new AdminPlaceKakaoPlaceIdUpdateResponse(
                mapPlace.getId(),
                mapPlace.getKakaoPlaceId(),
                "장소 Kakao place id를 수정했습니다."
        );
    }

    private String normalizeKakaoPlaceId(String kakaoPlaceId) {
        if (kakaoPlaceId == null) {
            return null;
        }
        return kakaoPlaceId.trim();
    }
}
