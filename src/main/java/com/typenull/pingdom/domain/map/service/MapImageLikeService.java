package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.firebase.service.FcmService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.MapImageLike;
import com.typenull.pingdom.domain.map.dto.MapImageLikeRequest;
import com.typenull.pingdom.domain.map.dto.MapImageLikeResponse;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageLikeRepository;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapImageLikeService {

    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapImageRepository mapImageRepository;
    private final FcmService fcmService;

    @Transactional
    public MapImageLikeResponse like(MapImageLikeRequest mapImageLikeRequest, Long userId){
        if(mapImageLikeRepository.existsByUserIdAndMapImageId(
                userId,
                mapImageLikeRequest.mapImageId()
        )) {
            throw new MapException(MapErrorCode.ALREADY_LIKED);
        }

        MapImage mapImage = mapImageRepository.findById(
                mapImageLikeRequest.mapImageId()
        ).orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        MapImageLike mapImageLike = MapImageLike.builder()
                .mapImageId(mapImageLikeRequest.mapImageId())
                .userId(userId)
                .build();

        mapImageLikeRepository.save(mapImageLike);
        mapImageRepository.increaseLikeCount(mapImageLikeRequest.mapImageId());
        try {
            fcmService.sendLikeNotification(mapImage, userId);
        } catch (Exception e) {
            log.error("FCM 알림 보내기 실패", e);
        }

        return new MapImageLikeResponse(userId,mapImageLikeRequest.mapImageId());
    }
}
